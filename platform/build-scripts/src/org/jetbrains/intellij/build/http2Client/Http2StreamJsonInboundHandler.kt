// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamFrame
import io.netty.util.AsciiString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import java.util.zip.GZIPInputStream

private val json = Json {
  ignoreUnknownKeys = true
}

internal suspend fun Http2ClientConnection.getString(path: CharSequence): String {
  return connection.stream { stream, result ->
    stream.pipeline().addLast(Http2StreamJsonInboundHandler(urlPath = path, result = result, bufferAllocator = stream.alloc(), deserializer = null))

    stream.writeHeaders(
      headers = createHeaders(
        HttpMethod.GET,
        AsciiString.of(path),
        HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP,
      ),
      endStream = true,
    )
  }
}

internal suspend inline fun <reified T : Any> Http2ClientConnection.getJsonOrDefaultIfNotFound(
  path: CharSequence,
  defaultIfNotFound: T,
): T {
  return doGetJsonOrDefaultIfNotFound(path = path, deserializer = serializer<T>(), defaultIfNotFound = defaultIfNotFound)
}

internal suspend inline fun <reified T : Any> Http2ClientConnection.post(path: CharSequence, data: CharSequence, contentType: AsciiString): T {
  return post(path = AsciiString.of(path), data = data, contentType = contentType, deserializer = serializer<T>())
}

private suspend fun <T : Any> Http2ClientConnection.doGetJsonOrDefaultIfNotFound(
  path: CharSequence,
  deserializer: KSerializer<T>,
  defaultIfNotFound: T,
): T {
  return connection.stream { stream, result ->
    stream.pipeline().addLast(Http2StreamJsonInboundHandler(
      urlPath = path,
      result = result,
      bufferAllocator = stream.alloc(),
      deserializer = deserializer,
      defaultIfNotFound = defaultIfNotFound,
    ))

    stream.writeHeaders(
      headers = createHeaders(
        HttpMethod.GET,
        AsciiString.of(path),
        HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP,
      ),
      endStream = true,
    )
  }
}

private suspend fun <T : Any> Http2ClientConnection.post(path: AsciiString, data: CharSequence, contentType: AsciiString, deserializer: DeserializationStrategy<T>): T {
  return connection.stream { stream, result ->
    val bufferAllocator = stream.alloc()
    stream.pipeline().addLast(Http2StreamJsonInboundHandler(urlPath = path, result = result, bufferAllocator = bufferAllocator, deserializer = deserializer))

    stream.writeHeaders(
      headers = createHeaders(
        HttpMethod.POST,
        path,
        HttpHeaderNames.CONTENT_TYPE, contentType,
        HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP,
      ),
      endStream = false,
    )
    stream.writeAndFlush(DefaultHttp2DataFrame(ByteBufUtil.writeUtf8(bufferAllocator, data), true)).joinCancellable()
  }
}

private class Http2StreamJsonInboundHandler<T : Any>(
  private val urlPath: CharSequence,
  private val bufferAllocator: ByteBufAllocator,
  private val result: CompletableDeferred<T>,
  private val deserializer: DeserializationStrategy<T>?,
  private val defaultIfNotFound: T? = null,
) : InboundHandlerResultTracker<Http2StreamFrame>(result) {
  private var isGzip = false

  private val cumulativeContent = lazy(LazyThreadSafetyMode.NONE) {
    val compositeBuffer = bufferAllocator.compositeBuffer(1024)
    result.invokeOnCompletion {
      compositeBuffer.release()
    }
    compositeBuffer
  }

  override fun acceptInboundMessage(message: Any) = message is Http2DataFrame || message is Http2HeadersFrame

  override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
    if (frame is Http2DataFrame) {
      val frameContent = frame.content()
      if (!frame.isEndStream) {
        cumulativeContent.value.addComponent(true, frameContent.retain())
        return
      }
      val data = if (cumulativeContent.isInitialized()) {
        cumulativeContent.value.addComponent(true, frameContent.retain())
        cumulativeContent.value
      }
      else {
        frameContent
      }

      val response = if (isGzip) {
        GZIPInputStream(ByteBufInputStream(data)).use {
          if (deserializer == null) {
            @Suppress("UNCHECKED_CAST")
            it.reader().readText() as T
          }
          else {
            json.decodeFromStream(deserializer, it)
          }
        }
      }
      else {
        val string = data.toString(Charsets.UTF_8)
        if (deserializer == null) {
          @Suppress("UNCHECKED_CAST")
          string as T
        }
        else {
          json.decodeFromString(deserializer, string)
        }
      }
      result.complete(response)
    }
    else if (frame is Http2HeadersFrame) {
      val status = HttpResponseStatus.parseLine(frame.headers().status())
      if (status != HttpResponseStatus.OK) {
        if (status == HttpResponseStatus.NOT_FOUND && defaultIfNotFound != null) {
          result.complete(defaultIfNotFound)
        }
        else {
          result.completeExceptionally(UnexpectedHttpStatus(urlPath = urlPath, status))
        }
        return
      }

      isGzip = frame.headers().get(HttpHeaderNames.CONTENT_ENCODING) == HttpHeaderValues.GZIP
    }
  }
}