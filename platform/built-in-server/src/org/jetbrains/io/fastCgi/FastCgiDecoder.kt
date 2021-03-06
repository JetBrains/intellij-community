// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io.fastCgi

import com.intellij.util.Consumer
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.util.CharsetUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.io.Decoder

internal const val HEADER_LENGTH = 8

private enum class DecodeRecordState {
  HEADER,
  CONTENT
}

internal class FastCgiDecoder(private val errorOutputConsumer: Consumer<String>, private val responseHandler: FastCgiService) : Decoder(), Decoder.FullMessageConsumer<Boolean> {
  private var state = DecodeRecordState.HEADER

  private enum class ProtocolStatus {
    REQUEST_COMPLETE,
    CANT_MPX_CONN,
    OVERLOADED,
    UNKNOWN_ROLE
  }

  object RecordType {
    const val END_REQUEST = 3
    const val STDOUT = 6
    const val STDERR = 7
  }

  private var type = 0
  private var id = 0
  private var contentLength: Int = 0
  private var paddingLength: Int = 0

  private val dataBuffers = Int2ObjectOpenHashMap<ByteBuf>()

  override fun messageReceived(context: ChannelHandlerContext, input: ByteBuf) {
    while (true) {
      when (state) {
        DecodeRecordState.HEADER -> {
          if (paddingLength > 0) {
            if (input.readableBytes() > paddingLength) {
              input.skipBytes(paddingLength)
              paddingLength = 0
            }
            else {
              paddingLength -= input.readableBytes()
              input.skipBytes(input.readableBytes())
              return
            }
          }

          val buffer = getBufferIfSufficient(input, HEADER_LENGTH, context) ?: return
          buffer.skipBytes(1)
          type = buffer.readUnsignedByte().toInt()
          id = buffer.readUnsignedShort()
          contentLength = buffer.readUnsignedShort()
          paddingLength = buffer.readUnsignedByte().toInt()
          buffer.skipBytes(1)
          state = DecodeRecordState.CONTENT
        }

        DecodeRecordState.CONTENT -> {
          if (contentLength > 0) {
            if (readContent(input, context, contentLength, this) == null) {
              return
            }
          }
          state = DecodeRecordState.HEADER
        }
      }
    }
  }

  override fun channelInactive(context: ChannelHandlerContext) {
    try {
      if (!dataBuffers.isEmpty()) {
        for (buffer in dataBuffers.values) {
          try {
            buffer.release()
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
        dataBuffers.clear()
      }
    }
    finally {
      super.channelInactive(context)
    }
  }

  override fun contentReceived(buffer: ByteBuf, context: ChannelHandlerContext, isCumulateBuffer: Boolean): Boolean {
    when (type) {
      RecordType.STDOUT -> {
        var data = dataBuffers.get(id)
        val sliced = if (isCumulateBuffer) buffer else buffer.slice(buffer.readerIndex(), contentLength)
        when (data) {
          null -> dataBuffers.put(id, sliced)
          is CompositeByteBuf -> {
            data.addComponent(sliced)
            data.writerIndex(data.writerIndex() + sliced.readableBytes())
          }
          else -> {
            if (sliced is CompositeByteBuf) {
              val readable = data.readableBytes()
              data = sliced.addComponent(0, data)
              data.writerIndex(data.writerIndex() + readable)
            }
            else {
              // must be computed here before we set data to new composite buffer
              val newLength = data.readableBytes() + sliced.readableBytes()
              data = context.alloc().compositeBuffer(Decoder.DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS).addComponents(data, sliced)
              data.writerIndex(data.writerIndex() + newLength)
            }
            dataBuffers.put(id, data)
          }
        }
        sliced.retain()
      }

      RecordType.STDERR -> {
        try {
          errorOutputConsumer.consume(buffer.toString(buffer.readerIndex(), contentLength, CharsetUtil.UTF_8))
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }

      RecordType.END_REQUEST -> {
        val appStatus = buffer.readInt()
        val protocolStatus = buffer.readUnsignedByte().toInt()
        if (appStatus != 0 || protocolStatus != ProtocolStatus.REQUEST_COMPLETE.ordinal) {
          LOG.warn("Protocol status $protocolStatus")
          dataBuffers.remove(id)
          responseHandler.responseReceived(id, null)
        }
        else if (protocolStatus == ProtocolStatus.REQUEST_COMPLETE.ordinal) {
          responseHandler.responseReceived(id, dataBuffers.remove(id))
        }
        else {
          LOG.warn("protocolStatus $protocolStatus")
        }
      }

      else -> {
        LOG.error("Unknown type $type")
      }
    }
    return true
  }
}