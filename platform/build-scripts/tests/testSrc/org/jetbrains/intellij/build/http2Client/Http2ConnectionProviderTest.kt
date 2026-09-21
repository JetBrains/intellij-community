// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.Zstd
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2MultiplexHandler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private const val URL_PATH = "/file.bin"

/**
 * Covers the reconnect of [Http2ConnectionProvider] after the server drops the TCP connection.
 * A local h2c server decides per request whether to answer or to drop the connection.
 */
@Timeout(60)
class Http2ConnectionProviderTest {
  @TempDir
  lateinit var tempDir: Path

  private val payload = Random(42).nextBytes(40 * 1024)

  @Test
  fun `drop mid-stream then recover`() {
    val compressed = Zstd.compress(payload)
    val file = tempDir.resolve("mid-stream.bin")
    runWithServer(
      onRequest = { stream, connectionIndex ->
        if (connectionIndex == 1) {
          stream.write(okHeaders())
          val partial = Unpooled.wrappedBuffer(compressed, 0, compressed.size / 2)
          stream.writeAndFlush(DefaultHttp2DataFrame(partial, false)).addListener { dropConnection(stream) }
        }
        else {
          respond(stream, compressed)
        }
      },
    ) { connection, server ->
      val result = connection.download(path = URL_PATH, file = file, zstdDecompressContextPool = ZstdDecompressContextPool())
      assertThat(result.size).isEqualTo(compressed.size.toLong())
      assertThat(Files.readAllBytes(file)).isEqualTo(payload)
      assertThat(server.acceptedConnections.get()).isEqualTo(2)
    }
  }

  @Test
  fun `server unreachable after drop`() {
    val file = tempDir.resolve("unreachable.bin")
    runWithServer(
      onRequest = { stream, _ ->
        stopAccepting().addListener { dropConnection(stream) }
      },
    ) { connection, server ->
      val error = runCatching { connection.download(path = URL_PATH, file = file) }.exceptionOrNull()
      assertThat(error).isExactlyInstanceOf(RuntimeException::class.java).hasMessage("3 attempts failed")
      assertThat(error!!.cause).isInstanceOf(ConnectException::class.java)
      assertThat(server.acceptedConnections.get()).isEqualTo(1)
    }
  }

  @Test
  fun `repeated drops stay bounded`() {
    val file = tempDir.resolve("repeated.bin")
    runWithServer(
      onRequest = { stream, _ -> dropConnection(stream) },
    ) { connection, server ->
      val error = runCatching { connection.download(path = URL_PATH, file = file) }.exceptionOrNull()
      val expectedReconnects = MAX_RECONNECTS + 1
      assertThat(error).isExactlyInstanceOf(RuntimeException::class.java).hasMessage("connection closed $expectedReconnects times")
      assertThat(server.acceptedConnections.get()).isEqualTo(expectedReconnects)
    }
  }

  @Test
  fun `normal shutdown after a successful download`() {
    val file = tempDir.resolve("normal.bin")
    runWithServer(
      onRequest = { stream, _ -> respond(stream, payload) },
    ) { connection, server ->
      val size = connection.download(path = URL_PATH, file = file)
      assertThat(size).isEqualTo(payload.size.toLong())
      assertThat(Files.readAllBytes(file)).isEqualTo(payload)
      assertThat(server.acceptedConnections.get()).isEqualTo(1)
    }
  }

  private fun runWithServer(
    onRequest: H2cTestServer.(stream: Channel, connectionIndex: Int) -> Unit,
    block: suspend (connection: Http2ClientConnection, server: H2cTestServer) -> Unit,
  ) {
    runBlocking {
      withTimeout(30.seconds) {
        H2cTestServer(onRequest).use { server ->
          withHttp2ClientConnectionFactory(useSsl = false) { factory ->
            factory.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), server.port)).use { connection ->
              block(connection, server)
            }
          }
        }
      }
    }
  }
}

private fun okHeaders(): DefaultHttp2HeadersFrame {
  return DefaultHttp2HeadersFrame(DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText()), false)
}

private fun respond(stream: Channel, body: ByteArray) {
  stream.write(okHeaders())
  stream.writeAndFlush(DefaultHttp2DataFrame(Unpooled.wrappedBuffer(body), true))
}

// close the TCP channel behind the HTTP/2 codec, so the server sends no GOAWAY and the client sees a plain network drop
private fun dropConnection(stream: Channel) {
  stream.parent().pipeline().firstContext().close()
}

/**
 * A plain-text HTTP/2 server on the loopback interface.
 * [onRequest] runs on the stream event loop for every request headers frame.
 */
private class H2cTestServer(
  private val onRequest: H2cTestServer.(stream: Channel, connectionIndex: Int) -> Unit,
) : AutoCloseable {
  @JvmField
  val acceptedConnections = AtomicInteger()

  private val group = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())

  private val serverChannel: Channel = ServerBootstrap()
    .group(group)
    .channel(NioServerSocketChannel::class.java)
    .childHandler(object : ChannelInitializer<Channel>() {
      override fun initChannel(channel: Channel) {
        val connectionIndex = acceptedConnections.incrementAndGet()
        channel.pipeline().addLast(Http2FrameCodecBuilder.forServer().build())
        channel.pipeline().addLast(Http2MultiplexHandler(object : ChannelInitializer<Channel>() {
          override fun initChannel(stream: Channel) {
            stream.pipeline().addLast(object : SimpleChannelInboundHandler<Http2HeadersFrame>() {
              override fun channelRead0(context: ChannelHandlerContext, frame: Http2HeadersFrame) {
                onRequest(context.channel(), connectionIndex)
              }
            })
          }
        }))
      }
    })
    .bind(InetAddress.getLoopbackAddress(), 0)
    .syncUninterruptibly()
    .channel()

  val port: Int
    get() = (serverChannel.localAddress() as InetSocketAddress).port

  // stop the listener - a new client connection is refused
  fun stopAccepting(): ChannelFuture = serverChannel.close()

  override fun close() {
    group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly()
  }
}
