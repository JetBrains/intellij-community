// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.testFramework.ApplicationRule
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.io.handler
import com.intellij.util.net.loopbackSocketAddress
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import junit.framework.TestCase
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.io.ChannelExceptionHandler
import org.jetbrains.io.Decoder
import org.jetbrains.io.MessageDecoder
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import java.util.concurrent.TimeUnit

// we don't handle String in efficient way - because we want to test readContent/readChars also
internal class BinaryRequestHandlerTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Test
  fun test() {
    val text = "Hello!"
    val result = AsyncPromise<String>()

    val builtInServerManager = BuiltInServerManager.getInstance().waitForStart()
    val bootstrap = builtInServerManager.createClientBootstrap().handler {
      it.pipeline().addLast(object : Decoder() {
        override fun messageReceived(context: ChannelHandlerContext, input: ByteBuf) {
          val requiredLength = 4 + text.length
          val response = readContent(input, context, requiredLength) { buffer, _, _ -> buffer.toString(buffer.readerIndex(), requiredLength, Charsets.UTF_8) }
          if (response != null) {
            result.setResult(response)
          }
        }
      }, ChannelExceptionHandler.getInstance())
    }

    val port = builtInServerManager.port
    val channel = bootstrap.connect(loopbackSocketAddress(port)).syncUninterruptibly().channel()
    val buffer = channel.alloc().buffer()
    buffer.writeByte('C'.code)
    buffer.writeByte('H'.code)
    buffer.writeLong(MyBinaryRequestHandler.ID.mostSignificantBits)
    buffer.writeLong(MyBinaryRequestHandler.ID.leastSignificantBits)

    val message = Unpooled.copiedBuffer(text, Charsets.UTF_8)
    buffer.writeShort(message.readableBytes())
    channel.write(buffer)
    channel.writeAndFlush(message).await(5, TimeUnit.SECONDS)

    try {
      result.onError { error -> TestCase.fail(error.message) }

      if (result.state == Promise.State.PENDING) {
        val semaphore = Semaphore()
        semaphore.down()
        result.onProcessed { semaphore.up() }
        if (!semaphore.waitForUnsafe(5000)) {
          TestCase.fail("Time limit exceeded")
          return
        }
      }

      TestCase.assertEquals("got-$text", result.get())
    }
    finally {
      channel.close()
    }
  }
}

private class MyBinaryRequestHandler : BinaryRequestHandler() {
  companion object {
    val ID: UUID = UUID.fromString("E5068DD6-1DB7-437C-A3FC-3CA53B6E1AC9")
  }

  override fun getId(): UUID = ID

  override fun getInboundHandler(context: ChannelHandlerContext): ChannelHandler = MyDecoder()

  private class MyDecoder : MessageDecoder() {
    private var state = State.HEADER

    private enum class State {
      HEADER,
      CONTENT
    }

    override fun messageReceived(context: ChannelHandlerContext, input: ByteBuf) {
      while (true) {
        when (state) {
          State.HEADER -> {
            val buffer = getBufferIfSufficient(input, 2, context) ?: return
            contentLength = buffer.readUnsignedShort()
            state = State.CONTENT
          }

          State.CONTENT -> {
            val messageText = readChars(input) ?: return

            state = State.HEADER
            context.writeAndFlush(Unpooled.copiedBuffer("got-$messageText", Charsets.UTF_8))
          }
        }
      }
    }
  }
}