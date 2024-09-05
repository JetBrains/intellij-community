// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufInputStream
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.util.zip.GZIPInputStream

private val json = Json {
  ignoreUnknownKeys = true
}

internal class Http2StreamJsonInboundHandler<T : Any>(
  private val bufferAllocator: ByteBufAllocator,
  private val result: CompletableDeferred<T>,
  private val deserializer: DeserializationStrategy<T>,
) : InboundHandlerResultTracker<Http2StreamFrame>(result) {
  private var isGzip = false

  private val cumulativeContent = lazy(LazyThreadSafetyMode.NONE) {
    val compositeBuffer = bufferAllocator.compositeBuffer(1024)
    result.invokeOnCompletion {
      compositeBuffer.release()
    }
    compositeBuffer
  }

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
          json.decodeFromStream(deserializer, it)
        }
      }
      else {
        json.decodeFromString(deserializer, data.toString(Charsets.UTF_8))
      }
      result.complete(response)
    }
    else if (frame is Http2HeadersFrame) {
      val status = HttpResponseStatus.parseLine(frame.headers().status())
      if (status != HttpResponseStatus.OK) {
        result.completeExceptionally(IllegalStateException("Failed with $status"))
        return
      }

      isGzip = frame.headers().get(HttpHeaderNames.CONTENT_ENCODING) == HttpHeaderValues.GZIP
    }
  }
}