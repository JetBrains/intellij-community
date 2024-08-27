// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package org.jetbrains.intellij.build.http2Client

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.SslContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetSocketAddress
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.random.Random
import kotlin.random.asJavaRandom
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class ConnectionState(
  @JvmField val bootstrap: Http2StreamChannelBootstrap,
  @JvmField val channel: Channel,
  @JvmField val coroutineScope: CoroutineScope,
)

private const val MAX_ATTEMPTS = 2

// https://stackoverflow.com/questions/55087292/how-to-handle-http-2-goaway-with-java-net-httpclient
// Server can send GOAWAY frame after X streams, that's why we need manager for channel - open a new one in case of such error
internal class Http2ConnectionProvider(
  private val server: InetSocketAddress,
  private val bootstrapTemplate: Bootstrap,
  private val ioDispatcher: CoroutineDispatcher,
  sslContext: SslContext?,
) {
  private val mutex = Mutex()
  private val channelInitializer = Http2ClientFrameInitializer(sslContext, server)
  private val connectionRef = AtomicReference<ConnectionState>()

  private suspend fun getConnection(): ConnectionState {
    connectionRef.get()?.takeIf { it.coroutineScope.isActive }?.let {
      return it
    }

    // not reentrant
    mutex.withLock {
      return connectionRef.get()?.takeIf { it.coroutineScope.isActive } ?: withContext(ioDispatcher) { openChannel() }
    }
  }

  // must be called with mutex
  private suspend fun openChannel(): ConnectionState {
    Span.current().addEvent("open TCP connection", Attributes.of(AttributeKey.stringKey("host"), server.hostString, AttributeKey.longKey("port"), server.port.toLong()))

    val channel = bootstrapTemplate
      .clone()
      .remoteAddress(server)
      .handler(channelInitializer)
      .connectAsync()

    val connection = ConnectionState(
      bootstrap = Http2StreamChannelBootstrap(channel),
      channel = channel,
      coroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
    )
    (channel.pipeline().get("Http2FrameCodec") as Http2FrameCodec).connection().addListener(object : Http2ConnectionAdapter() {
      override fun onGoAwayReceived(lastStreamId: Int, errorCode: Long, debugData: ByteBuf?) {
        connection.coroutineScope.coroutineContext.cancel()
        connectionRef.compareAndSet(connection, null)
      }
    })
    val old = connectionRef.getAndSet(connection)
    require(old == null || !old.coroutineScope.isActive) {
      "Old connection must be inactive before opening a new one"
    }
    return connection
  }

  suspend fun close() {
    val connection = connectionRef.get() ?: return
    try {
      connection.coroutineScope.coroutineContext.job.cancelAndJoin()
    }
    finally {
      connection.channel.close().joinNonCancellable()
    }
  }

  suspend fun <T> stream(block: suspend (streamChannel: Http2StreamChannel, result: CompletableDeferred<T>) -> Unit): T {
    var attemptIndex = 0
    var effectiveDelay = 1.seconds.inWholeMilliseconds
    val backOffLimitMs = 500.milliseconds.inWholeMilliseconds
    val backOffFactor = 2L
    val backOffJitter = 0.1
    var suppressedExceptions: MutableList<Throwable>? = null
    while (true) {
      var currentConnection: ConnectionState? = null
      try {
        currentConnection = getConnection()
        return withContext(currentConnection.coroutineScope.coroutineContext) {
          openStreamAndConsume(connectionState = currentConnection, block = block)
        }
      }
      catch (e: Http2Exception) {
        handleHttpError(e = e, attemptIndex = attemptIndex, currentConnection = currentConnection)
      }
      catch (e: CancellationException) {
        if (coroutineContext.isActive) {
          // task is canceled (due to GoAway or other such reasons), but not parent context - retry (without incrementing attemptIndex)
          continue
        }
      }
      catch (e: ClosedChannelException) {
        if (attemptIndex >= MAX_ATTEMPTS) {
          if (suppressedExceptions != null) {
            for (suppressedException in suppressedExceptions) {
              e.addSuppressed(suppressedException)
            }
          }
          throw e
        }

        if (suppressedExceptions == null) {
          suppressedExceptions = ArrayList()
        }
        suppressedExceptions.add(e)
        if (attemptIndex != 0) {
          delay(effectiveDelay)
        }

        effectiveDelay = min(effectiveDelay * backOffFactor, backOffLimitMs) + (Random.asJavaRandom().nextGaussian() * effectiveDelay * backOffJitter).toLong()
      }

      attemptIndex++
    }
  }

  private suspend fun handleHttpError(e: Http2Exception, attemptIndex: Int, currentConnection: ConnectionState?) {
    when (val error = e.error()) {
      Http2Error.REFUSED_STREAM -> {
        // result of goaway frame - open a new connection
        if (currentConnection != null) {
          currentConnection.coroutineScope.cancel()
          connectionRef.compareAndSet(currentConnection, null)
          Span.current().addEvent("stream refused", Attributes.of(AttributeKey.longKey("streamId"), Http2Exception.streamId(e).toLong()))
        }
      }
      Http2Error.ENHANCE_YOUR_CALM -> {
        delay(100L * (attemptIndex + 1))
      }
      else -> {
        if (attemptIndex >= MAX_ATTEMPTS) {
          throw e
        }
        else {
          // log error and continue
          Span.current().recordException(e, Attributes.of(AttributeKey.longKey("attemptIndex"), attemptIndex.toLong(), AttributeKey.stringKey("name"), error.name))
        }
      }
    }
  }

  // must be called with ioDispatcher
  private suspend fun <T> openStreamAndConsume(
    connectionState: ConnectionState,
    block: suspend (streamChannel: Http2StreamChannel, result: CompletableDeferred<T>) -> Unit,
  ): T {
    val streamChannel = connectionState.bootstrap.open().cancellableAwait()
    try {
      // must be canceled when the parent context is canceled
      val result = CompletableDeferred<T>(parent = coroutineContext.job)
      block(streamChannel, result)
      return result.await()
    }
    finally {
      if (streamChannel.isOpen && connectionState.coroutineScope.isActive) {
        withContext(NonCancellable) {
          streamChannel.close().joinNonCancellable()
        }
      }
    }
  }
}

private class Http2ClientFrameInitializer(
  private val sslContext: SslContext?,
  private val server: InetSocketAddress,
) : ChannelInitializer<Channel>() {
  override fun initChannel(channel: Channel) {
    val pipeline = channel.pipeline()
    sslContext?.let {
      pipeline.addFirst(sslContext.newHandler(channel.alloc(), server.hostString, server.port))
    }

    pipeline.addLast("Http2FrameCodec", Http2FrameCodecBuilder.forClient().build())
    // Http2MultiplexHandler requires not-null handlers for child streams - add noop
    pipeline.addLast("Http2MultiplexHandler", Http2MultiplexHandler(object : SimpleChannelInboundHandler<Any>() {
      override fun acceptInboundMessage(message: Any?): Boolean = false

      override fun channelRead0(ctx: ChannelHandlerContext, message: Any?) {
        // noop
      }
    }))
  }
}