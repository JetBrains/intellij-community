// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection", "UsePropertyAccessSyntax", "OVERRIDE_DEPRECATION")

package org.jetbrains.intellij.build.http2Client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.util.concurrent.Future
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal suspend fun Bootstrap.connectAsync(): Channel = suspendCancellableCoroutine { continuation ->
  val future = connect()

  if (future.isDone) {
    if (future.isSuccess) {
      continuation.resume(future.channel())
    }
    else {
      continuation.resumeWithException(future.cause())
    }
    return@suspendCancellableCoroutine
  }

  if (future.isCancellable) {
    continuation.invokeOnCancellation {
      future.cancel(true)
    }
  }

  future.addListener {
    if (future.isSuccess) {
      continuation.resume(future.channel())
    }
    else {
      continuation.resumeWithException(future.cause())
    }
  }
}

internal suspend fun <T : Any> Future<T>.cancellableAwait(): T {
  if (isDone) {
    if (isSuccess) {
      return getNow()
    }
    else {
      throw cause()
    }
  }

  return suspendCancellableCoroutine { continuation ->
    if (isCancellable) {
      continuation.invokeOnCancellation {
        cancel(true)
      }
    }

    addListener {
      if (isSuccess) {
        continuation.resume(get())
      }
      else {
        continuation.resumeWithException(cause())
      }
    }
  }
}

internal suspend fun Future<*>.joinCancellable(cancelFutureOnCancellation: Boolean = true) {
  if (isDone) {
    cause()?.let {
      throw it
    }
    return
  }

  suspendCancellableCoroutine { continuation ->
    if (cancelFutureOnCancellation && isCancellable) {
      continuation.invokeOnCancellation {
        cancel(true)
      }
    }

    addListener {
      if (isSuccess) {
        continuation.resume(Unit)
      }
      else {
        continuation.resumeWithException(cause())
      }
    }
  }
}

internal suspend fun Http2StreamChannel.writeHeaders(headers: Http2Headers, endStream: Boolean) {
  writeAndFlush(DefaultHttp2HeadersFrame(headers, endStream)).joinCancellable()
}

internal abstract class InboundHandlerResultTracker<T : Any>(
  private val result: CompletableDeferred<*>,
) : SimpleChannelInboundHandler<T>() {
  override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
    result.completeExceptionally(cause)
  }
}

// not suspendCancellableCoroutine - we must close the channel / event loop group
internal suspend fun Future<*>.joinNonCancellable() {
  if (isDone) {
    cause()?.let {
      throw it
    }
    return
  }

  // not suspendCancellableCoroutine - we must close the channel
  return suspendCoroutine { continuation ->
    addListener { future ->
      if (future.isSuccess) {
        continuation.resume(Unit)
      }
      else {
        continuation.resumeWithException(future.cause())
      }
    }
  }
}