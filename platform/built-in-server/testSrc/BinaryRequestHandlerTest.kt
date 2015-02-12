package org.jetbrains.ide

import io.netty.channel.ChannelInitializer
import io.netty.channel.Channel
import org.jetbrains.io.Decoder
import io.netty.channel.ChannelHandlerContext
import io.netty.buffer.ByteBuf
import com.intellij.util.Consumer
import java.util.UUID
import io.netty.channel.ChannelHandler
import com.intellij.openapi.util.AsyncResult
import io.netty.util.CharsetUtil
import org.jetbrains.io.ChannelExceptionHandler
import org.jetbrains.io.NettyUtil
import com.intellij.util.net.NetUtils
import io.netty.buffer.Unpooled
import junit.framework.TestCase
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test

public class BinaryRequestHandlerTest {
  private val fixtureManager = FixtureRule()

  private val _chain = RuleChain
      .outerRule(fixtureManager)

  Rule
  public fun getChain(): RuleChain = _chain

  Test
  public fun test() {
    val text = "Hello!"
    val result = AsyncResult<String>()

    val bootstrap = NettyUtil.oioClientBootstrap().handler(object : ChannelInitializer<Channel>() {
      override fun initChannel(channel: Channel) {
        channel.pipeline().addLast(object : Decoder() {
          override fun messageReceived(context: ChannelHandlerContext, message: ByteBuf) {
            val requiredLength = 4 + text.length()
            val buffer = getBufferIfSufficient(message, requiredLength, context)
            if (buffer == null) {
              message.release()
            }
            else {
              val response = buffer.toString(buffer.readerIndex(), requiredLength, CharsetUtil.UTF_8)
              buffer.skipBytes(requiredLength)
              buffer.release()
              result.setDone(response)
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
      result.doWhenRejected(object : Consumer<String> {
        override fun consume(error: String) {
          TestCase.fail(error)
        }
      })

      TestCase.assertEquals("got-" + text, result.getResultSync(5000))
    }
    finally {
      channel.close()
    }
  }

  class MyBinaryRequestHandler : BinaryRequestHandler() {
    class object {
      val ID = UUID.fromString("E5068DD6-1DB7-437C-A3FC-3CA53B6E1AC9")
    }

    override fun getId(): UUID {
      return ID
    }

    override fun getInboundHandler(): ChannelHandler {
      return MyDecoder()
    }

    private class MyDecoder : Decoder() {
      private var state = State.HEADER
      private var contentLength = -1

      private enum class State {
        HEADER
        CONTENT
      }

      override fun messageReceived(context: ChannelHandlerContext, message: ByteBuf) {
        while (true) {
          when (state) {
            State.HEADER -> {
              run {
                val buffer = getBufferIfSufficient(message, 2, context)
                if (buffer == null) {
                  message.release()
                  return
                }

                contentLength = buffer.readUnsignedShort()
                state = State.CONTENT
              }
              run {
                val buffer = getBufferIfSufficient(message, contentLength, context)
                if (buffer == null) {
                  message.release()
                  return
                }

                val messageText = buffer.toString(buffer.readerIndex(), contentLength, CharsetUtil.UTF_8)
                buffer.skipBytes(contentLength)
                state = State.HEADER
                context.writeAndFlush(Unpooled.copiedBuffer("got-" + messageText, CharsetUtil.UTF_8))
              }
            }

            State.CONTENT -> {
              val buffer = getBufferIfSufficient(message, contentLength, context)
              if (buffer == null) {
                message.release()
                return
              }
              val messageText = buffer.toString(buffer.readerIndex(), contentLength, CharsetUtil.UTF_8)
              buffer.skipBytes(contentLength)
              state = State.HEADER
              context.writeAndFlush(Unpooled.copiedBuffer("got-" + messageText, CharsetUtil.UTF_8))
            }
          }
        }
      }
    }
  }
}