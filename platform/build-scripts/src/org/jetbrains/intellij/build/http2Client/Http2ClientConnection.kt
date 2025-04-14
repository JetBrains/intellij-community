// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection", "ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.http2Client

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.ReadOnlyHttp2Headers
import io.netty.util.AsciiString

// user-facing API on top of implementation API Http2ConnectionProvider
internal class Http2ClientConnection internal constructor(
  private val scheme: AsciiString,
  private val authority: AsciiString,
  private val commonHeaders: Array<AsciiString>,
  @JvmField internal val connection: Http2ConnectionProvider,
) {
  suspend inline fun <T> use(crossinline block: suspend (Http2ClientConnection) -> T): T {
    return connection.use {
      block(this)
    }
  }

  suspend fun close() {
    connection.close()
  }

  suspend fun head(path: CharSequence): HttpResponseStatus {
    return connection.stream { stream, result ->
      stream.pipeline().addLast(object : SimpleChannelInboundHandler<Http2HeadersFrame>() {
        override fun channelRead0(context: ChannelHandlerContext, frame: Http2HeadersFrame) {
          result.complete(HttpResponseStatus.parseLine(frame.headers().status()))
        }

        override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
          result.completeExceptionally(cause)
        }
      })

      stream.writeHeaders(createHeaders(HttpMethod.HEAD, AsciiString.of(path)), endStream = true)
    }
  }

  suspend fun getRedirectLocation(path: CharSequence): CharSequence? {
    return connection.stream { stream, result ->
      stream.pipeline().addLast(object : SimpleChannelInboundHandler<Http2HeadersFrame>() {
        override fun channelRead0(context: ChannelHandlerContext, frame: Http2HeadersFrame) {
          result.complete(
            when (HttpResponseStatus.parseLine(frame.headers().status())) {
              HttpResponseStatus.FOUND, HttpResponseStatus.MOVED_PERMANENTLY, HttpResponseStatus.TEMPORARY_REDIRECT, HttpResponseStatus.SEE_OTHER -> {
                frame.headers().get(HttpHeaderNames.LOCATION)
              }
              else -> {
                null
              }
            }
          )
        }

        override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
          result.completeExceptionally(cause)
        }
      })

      stream.writeHeaders(createHeaders(HttpMethod.HEAD, AsciiString.of(path)), endStream = true)
    }
  }

  internal fun createHeaders(method: HttpMethod, path: AsciiString, vararg extraHeaders: AsciiString): Http2Headers {
    var otherHeaders = commonHeaders
    if (extraHeaders.isNotEmpty()) {
      otherHeaders += extraHeaders
    }
    return ReadOnlyHttp2Headers.clientHeaders(true, method.asciiName(), path, scheme, authority, *otherHeaders)
  }
}