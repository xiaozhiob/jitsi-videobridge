/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.rtp.rtp

import org.jitsi.rtp.extensions.clone
import org.jitsi.rtp.extensions.shiftDataLeft
import org.jitsi.rtp.extensions.shiftDataRight
import org.jitsi.rtp.extensions.subBuffer
import org.jitsi.rtp.extensions.unsigned.toPositiveInt
import org.jitsi.rtp.util.ByteBufferUtils
import java.nio.ByteBuffer

/**
 * There are 3 different paths for creating an [RtxPacket]:
 * 1) Encapsulating an existing RTP packet in RTX to be sent
 * out as a retransmission
 * 2) Converting an [RtpPacket] instance which was discovered to
 * actually be an RTX packet.
 * 3) Parsing an incoming RTX packet from the network (such that
 * the original RTP packet can be extracted).
 *
 * An instance of [RtxPacket] will have a header corresponding
 * to the RTX version of this packet, and a payload which contains
 * the original RTP payload (NOT the original sequence number).
 * The original sequence number will be added when [serializeTo]
 * is called.
 */
class RtxPacket internal constructor(
    header: RtpHeader = RtpHeader(),
    payloadLength: Int = 0,
    backingBuffer: ByteBuffer = ByteBufferUtils.EMPTY_BUFFER
) : RtpPacket(header, payloadLength, backingBuffer) {

    override val sizeBytes: Int
        get() = super.sizeBytes + 2

    val originalSequenceNumber: Int = backingBuffer.getShort(header.sizeBytes).toPositiveInt()

    //TODO: will this work correctly with the new scheme?
    override fun serializeTo(buf: ByteBuffer) {
        header.serializeTo(buf)
//        buf.putShort(originalSequenceNumber.toShort())
        buf.put(payload)
    }

    fun getOriginalRtpPacket(): RtpPacket {
        return toOtherRtpPacketType { rtpHeader, payloadLength, backingBuffer ->
            backingBuffer.shiftDataLeft(rtpHeader.sizeBytes + 2, rtpHeader.sizeBytes + payloadLength - 1, 2)
            backingBuffer.limit(backingBuffer.limit() - 2)
            RtpPacket(rtpHeader, payloadLength - 2, backingBuffer)
        }
    }

    companion object {
        // Create an RTX packet (to be sent out) from a previously-sent
        // RTP packet.  NOTE: we do NOT want to modify the given RTP
        // packet, as it could be cached and we may want to do other
        // things with it in the future (including, for example, creating
        // another RTX packet from it)
        // TODO(brian): this is a good use case for being able to mark a
        // packet as immutable (at least to throw at runtime)
        fun fromRtpPacket(rtpPacket: RtpPacket): RtxPacket {
            return rtpPacket.toOtherRtpPacketType { rtpHeader, payloadLength, backingBuffer ->
                val clonedBuffer = backingBuffer.clone()
                // We need to move the payload to make room for the original sequence number
                clonedBuffer.shiftDataRight(rtpHeader.sizeBytes, rtpHeader.sizeBytes + payloadLength, 2)
                clonedBuffer.putShort(rtpHeader.sizeBytes, rtpPacket.header.sequenceNumber.toShort())

                RtxPacket(rtpHeader.clone(), payloadLength + 2, clonedBuffer)
            }
        }
        // Parse a buffer as an RTX packet
        fun fromBuffer(buf: ByteBuffer): RtxPacket {
            val header = RtpHeader.fromBuffer(buf)
            val payloadLength = buf.limit() - header.sizeBytes

            return RtxPacket(header, payloadLength, buf)
        }
    }
}