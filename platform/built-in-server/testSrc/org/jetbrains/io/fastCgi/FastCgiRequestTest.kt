// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io.fastCgi

import io.netty.buffer.ByteBuf
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Assert.assertEquals
import org.junit.Test

internal class FastCgiRequestTest {
  @Test
  fun `file headers carry the paths that the FastCGI server sees`() {
    val request = FastCgiRequest(1, UnpooledByteBufAllocator.DEFAULT)

    request.writeFileHeaders("/var/www", "/var/www/index.php", "/index.php")

    assertEquals(mapOf("DOCUMENT_ROOT" to "/var/www", "SCRIPT_FILENAME" to "/var/www/index.php", "SCRIPT_NAME" to "/index.php"),
                 params(request))
  }

  private fun params(request: FastCgiRequest): Map<String, String> {
    val channel = EmbeddedChannel(ChannelInboundHandlerAdapter())
    request.writeToServerChannel(null, channel)
    val buffer = channel.readOutbound<ByteBuf>()
    try {
      // The begin-request record, a header and a body of the same length, then the header of the params record, whose length is at 4.
      buffer.skipBytes(3 * RECORD_LENGTH)
      val paramsLength = buffer.getUnsignedShort(buffer.readerIndex() - RECORD_LENGTH + 4)
      val params = buffer.readSlice(paramsLength)
      val result = LinkedHashMap<String, String>()
      while (params.isReadable) {
        val nameLength = readLength(params)
        val valueLength = readLength(params)
        result[params.readCharSequence(nameLength, Charsets.UTF_8).toString()] = params.readCharSequence(valueLength, Charsets.UTF_8).toString()
      }
      return result
    }
    finally {
      buffer.release()
      channel.finishAndReleaseAll()
    }
  }

  private fun readLength(buffer: ByteBuf): Int =
    if (buffer.getUnsignedByte(buffer.readerIndex()).toInt() and 0x80 == 0) buffer.readUnsignedByte().toInt()
    else buffer.readInt() and 0x7FFFFFFF
}

private const val RECORD_LENGTH = 8
