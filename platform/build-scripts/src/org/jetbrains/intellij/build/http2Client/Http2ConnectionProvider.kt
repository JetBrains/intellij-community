// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection")

package org.jetbrains.intellij.build.http2Client

import com.intellij.platform.util.coroutines.childScope
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoop
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpStatusClass
import io.netty.handler.codec.http2.Http2ConnectionAdapter
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2Exception
import io.netty.handler.codec.http2.Http2FrameCodec
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2MultiplexHandler
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap
import io.netty.handler.ssl.SslContext
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

internal class UnexpectedHttpStatus(urlPath: CharSequence?, @JvmField val status: HttpResponseStatus)
  : RuntimeException("Unexpected HTTP response status: $status" + (if (urlPath == null) "" else " (urlPath=$urlPath)"))

private class ConnectionState(
  @JvmField val bootstrap: Http2StreamChannelBootstrap,
  @JvmField val channel: Channel,
  @JvmField val coroutineScope: CoroutineScope,
)

private const val MAX_ATTEMPTS = 3

// https://cabulous.medium.com/http-2-and-how-it-works-9f645458e4b2
// https://stackoverflow.com/questions/55087292/how-to-handle-http-2-goaway-with-java-net-httpclient
// Server can send GOAWAY frame after X streams, that's why we need manager for channel - open a new one in case of such an error
internal class Http2ConnectionProvider(
  private val server: InetSocketAddress,
  private val bootstrapTemplate: Bootstrap,
  private val ioDispatcher: CoroutineDispatcher,
  sslContext: SslContext?,
  private val coroutineScope: CoroutineScope,
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
      coroutineScope = coroutineScope.childScope("TCP connection (server=${server.hostString}:${server.port})"),
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

  suspend fun <T> use(block: suspend () -> T): T {
    try {
      return block()
    }
    finally {
      val connection = connectionRef.get()
      if (connection != null) {
        try {
          connection.coroutineScope.coroutineContext.job.cancelAndJoin()
        }
        finally {
          withContext(NonCancellable) {
            connection.channel.close().joinNonCancellable()
          }
        }
      }
    }
  }

  suspend fun close() {
    val connection = connectionRef.get() ?: return
    try {
      connection.coroutineScope.coroutineContext.job.cancelAndJoin()
    }
    finally {
      withContext(NonCancellable) {
        connection.channel.close().joinNonCancellable()
      }
    }
  }

  suspend fun <T> stream(block: suspend (streamChannel: Http2StreamChannel, result: CompletableDeferred<T>) -> Unit): T {
    var attempt = 1
    var suppressedExceptions: MutableList<Throwable>? = null
    while (true) {
      var currentConnection: ConnectionState? = null
      try {
        currentConnection = getConnection()
        // use a single-thread executor as Netty does for handlers for thread safety and fewer context switches
        val eventLoop = bootstrapTemplate.config().group().next()
        return withContext(currentConnection.coroutineScope.coroutineContext + EventLoopCoroutineDispatcher(eventLoop)) {
          openStreamAndConsume(connectionState = currentConnection, block = block)
        }
      }
      catch (e: Http2Exception) {
        handleHttpError(e = e, attempt = attempt, currentConnection = currentConnection)
      }
      catch (e: CancellationException) {
        if (coroutineContext.isActive) {
          // task is canceled (due to GoAway or other such reasons), but not parent context - retry (without incrementing attemptIndex)
          continue
        }
        else {
          throw e
        }
      }
      catch (e: UnexpectedHttpStatus) {
        // retry only for server errors
        if (e.status.codeClass() != HttpStatusClass.SERVER_ERROR) {
          throw e
        }
      }
      catch (e: Throwable) {
        if (attempt >= MAX_ATTEMPTS || e is IndexOutOfBoundsException) {
          if (suppressedExceptions != null) {
            for (suppressedException in suppressedExceptions) {
              e.addSuppressed(suppressedException)
            }
          }
          throw RuntimeException("$attempt attempts failed", e)
        }

        if (suppressedExceptions == null) {
          suppressedExceptions = ArrayList()
        }
        suppressedExceptions.add(e)

        Span.current().recordException(e, Attributes.of(AttributeKey.longKey("attemptIndex"), attempt.toLong()))


        delay(Random.nextLong(300, attempt * 3_000L))
      }

      attempt++
    }
  }

  private suspend fun handleHttpError(e: Http2Exception, attempt: Int, currentConnection: ConnectionState?) {
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
        delay(5000)
        Span.current().addEvent("enhance your calm", Attributes.of(AttributeKey.longKey("attempt"), attempt.toLong(), AttributeKey.stringKey("name"), error.name))
      }
      else -> {
        if (attempt >= MAX_ATTEMPTS) {
          throw e
        }
        else {
          // log error and continue
          Span.current().recordException(e, Attributes.of(AttributeKey.longKey("attempt"), attempt.toLong(), AttributeKey.stringKey("name"), error.name))
        }
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
    val deferred = CompletableDeferred<T>(parent = coroutineContext.job)
    block(streamChannel, deferred)

    // 1. writer must send the last data frame with endStream=true
    // 2. stream now has the half-closed state - we listen for server header response with endStream
    // 3. our ChannelInboundHandler above checks status and Netty closes the stream (as endStream was sent by both client and server)
    return deferred.await()
  }
  finally {
    if (streamChannel.isOpen && connectionState.coroutineScope.isActive) {
      withContext(NonCancellable) {
        streamChannel.close().joinNonCancellable()
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
    pipeline.addLast("Http2MultiplexHandler", Http2MultiplexHandler(object : ChannelInboundHandlerAdapter() {
    }))
  }
}

internal class EventLoopCoroutineDispatcher(@JvmField val eventLoop: EventLoop) : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    eventLoop.execute(block)
  }

  override fun isDispatchNeeded(context: CoroutineContext) = !eventLoop.inEventLoop()
}