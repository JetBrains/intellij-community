package org.jetbrains.io.fastCgi

import com.intellij.util.Consumer
import gnu.trove.TIntObjectHashMap
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.util.CharsetUtil
import org.jetbrains.io.Decoder

internal const val HEADER_LENGTH = 8

internal class FastCgiDecoder(private val errorOutputConsumer: Consumer<String>, private val responseHandler: FastCgiService) : Decoder(), Decoder.FullMessageConsumer<Void> {
  private enum class State {
    HEADER,
    CONTENT
  }

  private var state = State.HEADER

  private enum class ProtocolStatus {
    REQUEST_COMPLETE,
    CANT_MPX_CONN,
    OVERLOADED,
    UNKNOWN_ROLE
  }

  object RecordType {
    val END_REQUEST = 3
    val STDOUT = 6
    val STDERR = 7
  }

  private var type: Int = 0
  private var id: Int = 0
  private var contentLength: Int = 0
  private var paddingLength: Int = 0

  private val dataBuffers = TIntObjectHashMap<ByteBuf>()

  override fun messageReceived(context: ChannelHandlerContext, input: ByteBuf) {
    while (true) {
      when (state) {
        FastCgiDecoder.State.HEADER -> {
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

          decodeHeader(buffer)
          state = State.CONTENT

          if (contentLength > 0) {
            readContent(input, context, contentLength, this)
          }
          state = State.HEADER
        }

        FastCgiDecoder.State.CONTENT -> {
          if (contentLength > 0) {
            readContent(input, context, contentLength, this)
          }
          state = State.HEADER
        }
      }
    }
  }

  override fun channelInactive(context: ChannelHandlerContext) {
    try {
      if (!dataBuffers.isEmpty) {
        dataBuffers.forEachEntry { a, buffer ->
          try {
            buffer.release()
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
          true
        }
        dataBuffers.clear()
      }
    }
    finally {
      super.channelInactive(context)
    }
  }

  private fun decodeHeader(buffer: ByteBuf) {
    buffer.skipBytes(1)
    type = buffer.readUnsignedByte().toInt()
    id = buffer.readUnsignedShort()
    contentLength = buffer.readUnsignedShort()
    paddingLength = buffer.readUnsignedByte().toInt()
    buffer.skipBytes(1)
  }

  override fun contentReceived(buffer: ByteBuf, context: ChannelHandlerContext, isCumulateBuffer: Boolean): Void? {
    when (type) {
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
      }

      RecordType.STDOUT -> {
        var data = dataBuffers.get(id)
        val sliced = if (isCumulateBuffer) buffer else buffer.slice(buffer.readerIndex(), contentLength)
        if (data == null) {
          dataBuffers.put(id, sliced)
        }
        else if (data is CompositeByteBuf) {
          data.addComponent(sliced)
          data.writerIndex(data.writerIndex() + sliced.readableBytes())
        }
        else {
          if (sliced is CompositeByteBuf) {
            data = sliced.addComponent(0, data)
            data.writerIndex(data.writerIndex() + data.readableBytes())
          }
          else {
            // must be computed here before we set data to new composite buffer
            val newLength = data.readableBytes() + sliced.readableBytes()
            data = context.alloc().compositeBuffer(Decoder.DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS).addComponents(data, sliced)
            data.writerIndex(data.writerIndex() + newLength)
          }
          dataBuffers.put(id, data)
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

      else -> LOG.error("Unknown type $type")
    }
    return null
  }
}