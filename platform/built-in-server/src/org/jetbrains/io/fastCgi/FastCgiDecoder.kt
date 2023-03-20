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

internal class FastCgiDecoder(private val errorOutputConsumer: Consumer<String>, private val responseHandler: FastCgiService) : Decoder() {

  private object ProtocolStatus {
    const val REQUEST_COMPLETE = 0
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
    while (input.readableBytes() > 0) {
      if (contentLength > 0) {
        val toRead = minOf(contentLength, input.readableBytes())
        val readFromInput = contentReceived(input, toRead, context)
        input.skipBytes(toRead - readFromInput)
        contentLength -= toRead
      }
      else if (paddingLength > 0) {
        val toRead = minOf(paddingLength, input.readableBytes())
        input.skipBytes(toRead)
        paddingLength -= toRead
      }
      else {
        input.skipBytes(1) // version, expected to be 1
        type = input.readUnsignedByte().toInt()
        id = input.readUnsignedShort()
        contentLength = input.readUnsignedShort()
        paddingLength = input.readUnsignedByte().toInt()
        input.skipBytes(1) // reserved
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

  private fun contentReceived(buffer: ByteBuf, contentInBufferLength: Int, context: ChannelHandlerContext): Int {
    when (type) {
      RecordType.STDOUT -> {
        val data = dataBuffers.get(id)
        val sliced = buffer.slice(buffer.readerIndex(), contentInBufferLength)
        when (data) {
          null -> {
            dataBuffers.put(id, sliced)
          }
          is CompositeByteBuf -> {
            data.addComponent(sliced)
            data.writerIndex(data.writerIndex() + contentInBufferLength)
          }
          else -> {
            val compositeByteBuf = context.alloc().compositeBuffer(DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS)
            compositeByteBuf.addComponent(data)
            compositeByteBuf.addComponent(sliced)
            compositeByteBuf.writerIndex(data.writerIndex() + contentInBufferLength)
            dataBuffers.put(id, compositeByteBuf)
          }
        }
        sliced.retain()
        return 0
      }

      RecordType.STDERR -> {
        try {
          errorOutputConsumer.consume(buffer.toString(buffer.readerIndex(), contentInBufferLength, CharsetUtil.UTF_8))
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
        return 0
      }

      RecordType.END_REQUEST -> {
        val appStatus = buffer.readInt()
        val protocolStatus = buffer.readUnsignedByte().toInt()
        if (appStatus != 0 || protocolStatus != ProtocolStatus.REQUEST_COMPLETE) {
          LOG.warn("Protocol status $protocolStatus")
          dataBuffers.remove(id)
          responseHandler.responseReceived(id, null)
        }
        else {
          assert(protocolStatus == ProtocolStatus.REQUEST_COMPLETE)
          responseHandler.responseReceived(id, dataBuffers.remove(id))
        }
        return 5
      }

      else -> {
        LOG.error("Unknown type $type")
        return 0
      }
    }
  }
}