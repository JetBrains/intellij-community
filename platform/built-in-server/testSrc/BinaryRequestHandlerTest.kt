package org.jetbrains.ide

import com.intellij.util.Consumer
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.net.NetUtils
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.util.CharsetUtil
import junit.framework.TestCase
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.io.ChannelExceptionHandler
import org.jetbrains.io.Decoder
import org.jetbrains.io.MessageDecoder
import org.jetbrains.io.NettyUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.*

// we don't handle String in efficient way - because we want to test readContent/readChars also
public class BinaryRequestHandlerTest {
  private val fixtureManager = FixtureRule()

  private val _chain = RuleChain
      .outerRule(fixtureManager)

  @Rule
  public fun getChain(): RuleChain = _chain

  @Test
  public fun test() {
    val text = "Hello!"
    val result = AsyncPromise<String>()

    val bootstrap = NettyUtil.oioClientBootstrap().handler(object : ChannelInitializer<Channel>() {
      override fun initChannel(channel: Channel) {
        channel.pipeline().addLast(object : Decoder() {
          override fun messageReceived(context: ChannelHandlerContext, input: ByteBuf) {
            val requiredLength = 4 + text.length()
            val response = readContent(input, context, requiredLength) {buffer, context, isCumulateBuffer -> buffer.toString(buffer.readerIndex(), requiredLength, CharsetUtil.UTF_8) }
            if (response != null) {
              result.setResult(response)
            }
          }
        }, ChannelExceptionHandler.getInstance())
      }
    })

    val port = BuiltInServerManager.getInstance().waitForStart().getPort()
    val channel = bootstrap.connect(NetUtils.getLoopbackAddress(), port).syncUninterruptibly().channel()
    val buffer = channel.alloc().buffer()
    buffer.writeByte('C'.toInt())
    buffer.writeByte('H'.toInt())
    buffer.writeLong(MyBinaryRequestHandler.ID.getMostSignificantBits())
    buffer.writeLong(MyBinaryRequestHandler.ID.getLeastSignificantBits())

    val message = Unpooled.copiedBuffer(text, CharsetUtil.UTF_8)
    buffer.writeShort(message.readableBytes())
    channel.write(buffer)
    channel.writeAndFlush(message).syncUninterruptibly()

    try {
      result.rejected(object : Consumer<Throwable> {
        override fun consume(error: Throwable) {
          TestCase.fail(error.getMessage())
        }
      })

      if (result.getState() == Promise.State.PENDING) {
        val semaphore = Semaphore()
        semaphore.down()
        result.processed { semaphore.up() }
        if (!semaphore.waitForUnsafe(5000)) {
          TestCase.fail("Time limit exceeded")
          return
        }
      }

      TestCase.assertEquals("got-" + text, result.get())
    }
    finally {
      channel.close()
    }
  }

  class MyBinaryRequestHandler : BinaryRequestHandler() {
    companion object {
      val ID = UUID.fromString("E5068DD6-1DB7-437C-A3FC-3CA53B6E1AC9")
    }

    override fun getId(): UUID {
      return ID
    }

    override fun getInboundHandler(context: ChannelHandlerContext): ChannelHandler {
      return MyDecoder()
    }

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
              val buffer = getBufferIfSufficient(input, 2, context)
              if (buffer == null) {
                return
              }

              contentLength = buffer.readUnsignedShort()
              state = State.CONTENT
            }

            State.CONTENT -> {
              val messageText = readChars(input)
              if (messageText == null) {
                return
              }

              state = State.HEADER
              context.writeAndFlush(Unpooled.copiedBuffer("got-" + messageText, CharsetUtil.UTF_8))
            }
          }
        }
      }
    }
  }
}