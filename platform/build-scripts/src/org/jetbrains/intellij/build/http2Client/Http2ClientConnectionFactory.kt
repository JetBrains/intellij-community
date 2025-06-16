// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.epoll.EpollIoHandler
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.kqueue.KQueueIoHandler
import io.netty.channel.kqueue.KQueueSocketChannel
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http2.Http2SecurityUtil
import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.SupportedCipherSuiteFilter
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.util.AsciiString
import io.netty.util.internal.PlatformDependent
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.supervisorScope
import java.net.InetSocketAddress
import java.net.URI
import kotlin.coroutines.CoroutineContext

private fun nioIoHandlerAndSocketChannel() = NioIoHandler.newFactory() to NioSocketChannel::class.java

private val commonHeaders = arrayOf(
  HttpHeaderNames.USER_AGENT, AsciiString.of("IJ Builder"),
  AsciiString.of("x-tc-build-id"), AsciiString.of(System.getProperty("teamcity.buildType.id", ""))
)

internal class Http2ClientConnectionFactory(
  private val bootstrapTemplate: Bootstrap,
  private val sslContext: SslContext?,
  private val ioDispatcher: CoroutineDispatcher,
  private val coroutineScope: CoroutineScope,
) {
  fun connect(host: String, port: Int = 443, authHeader: CharSequence? = null): Http2ClientConnection {
    return connect(InetSocketAddress.createUnresolved(host, port.let { if (it == -1) 443 else it }), authHeader)
  }

  fun connect(address: URI, authHeader: CharSequence? = null): Http2ClientConnection {
    return connect(host = address.host, port = address.port, authHeader = authHeader)
  }

  fun connect(server: InetSocketAddress, auth: CharSequence? = null): Http2ClientConnection {
    var commonHeaders = commonHeaders
    if (auth != null) {
      commonHeaders += arrayOf(HttpHeaderNames.AUTHORIZATION, AsciiString.of(auth))
    }
    return Http2ClientConnection(
      connection = Http2ConnectionProvider(
        server = server,
        bootstrapTemplate = bootstrapTemplate,
        sslContext = sslContext,
        ioDispatcher = ioDispatcher,
        coroutineScope = coroutineScope,
      ),
      scheme = AsciiString.of(if (sslContext == null) "http" else "https"),
      authority = AsciiString.of(server.hostString + ":" + server.port),
      commonHeaders = commonHeaders,
    )
  }

  suspend fun close() {
    bootstrapTemplate.config().group().shutdownGracefully().joinNonCancellable()
  }
}

internal suspend fun <T> withHttp2ClientConnectionFactory(
  useSsl: Boolean = true,
  trustAll: Boolean = false,
  block: suspend (Http2ClientConnectionFactory) -> T,
): T {
  var connection: Http2ClientConnectionFactory? = null
  try {
    return supervisorScope {
      block(createHttp2ClientSessionFactory(useSsl = useSsl, trustAll = trustAll, coroutineScope = this).also {
        connection = it
      })
    }
  }
  finally {
    connection?.close()
  }
}

private fun createHttp2ClientSessionFactory(
  useSsl: Boolean = true,
  trustAll: Boolean = false,
  coroutineScope: CoroutineScope,
): Http2ClientConnectionFactory {
  val (ioFactory, socketChannel) = try {
    when {
      PlatformDependent.isOsx() -> KQueueIoHandler.newFactory() to KQueueSocketChannel::class.java
      !PlatformDependent.isWindows() -> EpollIoHandler.newFactory() to EpollSocketChannel::class.java
      else -> nioIoHandlerAndSocketChannel()
    }
  }
  catch (e: Throwable) {
    Span.current().recordException(e, Attributes.of(AttributeKey.stringKey("warn"), "cannot use native transport, fallback to NIO"))
    nioIoHandlerAndSocketChannel()
  }

  val group = MultiThreadIoEventLoopGroup(ioFactory)

  val sslContext: SslContext? = if (useSsl) {
    val isOpenSslSupported = SslProvider.isAlpnSupported(SslProvider.OPENSSL)
    SslContextBuilder.forClient()
      .sslProvider(if (isOpenSslSupported) SslProvider.OPENSSL else SslProvider.JDK)
      .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
      .applicationProtocolConfig(
        ApplicationProtocolConfig(
          ApplicationProtocolConfig.Protocol.ALPN,
          ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
          // OpenSSL provider does not support FATAL_ALERT behavior
          ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
          ApplicationProtocolNames.HTTP_2,
        )
      )
      .also {
        if (trustAll) {
          it.trustManager(InsecureTrustManagerFactory.INSTANCE)
        }
      }
      .build()
  }
  else {
    null
  }

  // use JDK DNS resolver - custom one is not easy to configure and overkill in our case (just several host names)
  val bootstrapTemplate = Bootstrap()
    .group(group)
    .channel(socketChannel)
    .option(ChannelOption.SO_KEEPALIVE, true)

  // asCoroutineDispatcher is overkill - we don't need schedule (`Delay`) features
  val dispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
      group.execute(block)
    }

    override fun toString(): String = group.toString()
  }

  return Http2ClientConnectionFactory(
    bootstrapTemplate = bootstrapTemplate,
    sslContext = sslContext,
    ioDispatcher = dispatcher,
    coroutineScope = coroutineScope,
  )
}