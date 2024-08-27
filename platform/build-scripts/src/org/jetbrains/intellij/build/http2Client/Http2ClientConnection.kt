// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection", "ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.http2Client

import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.Http2Headers
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.handler.codec.http2.ReadOnlyHttp2Headers
import io.netty.util.AsciiString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer

// https://cabulous.medium.com/http-2-and-how-it-works-9f645458e4b2
internal class Http2ClientConnection internal constructor(
  private val scheme: AsciiString,
  private val authority: AsciiString,
  private val commonHeaders: Array<AsciiString>,
  @JvmField internal val connection: Http2ConnectionProvider,
) {
  suspend inline fun <T> use(block: (Http2ClientConnection) -> T): T {
    try {
      return block(this)
    }
    finally {
      withContext(NonCancellable) {
        close()
      }
    }
  }

  suspend fun close() {
    connection.close()
  }

  internal suspend inline fun <reified T : Any> post(path: String, data: String, contentType: AsciiString): T {
    return post(path = AsciiString.of(path), data = data, contentType = contentType, deserializer = serializer<T>())
  }

  private suspend fun <T : Any> post(path: AsciiString, data: String, contentType: AsciiString, deserializer: DeserializationStrategy<T>): T {
    return connection.stream { stream, result ->
      val bufferAllocator = stream.alloc()
      stream.pipeline().addLast(Http2StreamJsonInboundHandler(result = result, bufferAllocator = bufferAllocator, deserializer = deserializer))

      stream.writeHeaders(
        headers = ReadOnlyHttp2Headers.clientHeaders(
          true,
          HttpMethod.POST.asciiName(),
          path,
          scheme,
          authority,
          HttpHeaderNames.CONTENT_TYPE, contentType,
          HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP,
          *commonHeaders
        ),
        endStream = false,
      )
      stream.writeData(ByteBufUtil.writeUtf8(bufferAllocator, data), endStream = true)
    }
  }

  suspend fun head(path: CharSequence): HttpResponseStatus {
    return connection.stream { stream, result ->
      stream.pipeline().addLast(object : InboundHandlerResultTracker<Http2HeadersFrame>(result) {
        override fun channelRead0(context: ChannelHandlerContext, frame: Http2HeadersFrame) {
          result.complete(HttpResponseStatus.parseLine(frame.headers().status()))
        }
      })

      stream.writeHeaders(createHeaders(HttpMethod.HEAD, AsciiString.of(path)), endStream = true)
    }
  }

  suspend fun getRedirectLocation(path: CharSequence): CharSequence? {
    return connection.stream { stream, result ->
      stream.pipeline().addLast(object : InboundHandlerResultTracker<Http2HeadersFrame>(result) {
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
      })

      stream.writeHeaders(createHeaders(HttpMethod.HEAD, AsciiString.of(path)), endStream = true)
    }
  }

  suspend fun put(path: AsciiString, writer: suspend (stream: Http2StreamChannel) -> Unit) {
    connection.stream { stream, result ->
      stream.pipeline().addLast(WebDavPutStatusChecker(result))

      stream.writeHeaders(createHeaders(HttpMethod.PUT, path), endStream = false)
      writer(stream)

      // 1. writer must send the last data frame with endStream=true
      // 2. stream now has the half-closed state - we listen for server header response with endStream
      // 3. our ChannelInboundHandler above checks status and Netty closes the stream (as endStream was sent by both client and server)
    }
  }

  internal fun createHeaders(method: HttpMethod, path: AsciiString): Http2Headers {
    return ReadOnlyHttp2Headers.clientHeaders(true, method.asciiName(), path, scheme, authority, *commonHeaders)
  }
}

private class WebDavPutStatusChecker(private val result: CompletableDeferred<Unit>) : InboundHandlerResultTracker<Http2HeadersFrame>(result) {
  override fun channelRead0(context: ChannelHandlerContext, frame: Http2HeadersFrame) {
    if (!frame.isEndStream) {
      return
    }

    val status = HttpResponseStatus.parseLine(frame.headers().status())
    // WebDAV server returns 204 for existing resources
    if (status == HttpResponseStatus.CREATED || status == HttpResponseStatus.NO_CONTENT || status == HttpResponseStatus.OK) {
      result.complete(Unit)
    }
    else {
      result.completeExceptionally(IllegalStateException("Unexpected response status: $status"))
    }
  }
}