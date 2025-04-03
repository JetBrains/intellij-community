// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.Http2DataFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamFrame
import io.netty.util.AsciiString
import kotlinx.coroutines.CompletableDeferred
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

internal data class DownloadResult(@JvmField var size: Long, @JvmField val digest: MessageDigest?)

internal suspend fun Http2ClientConnection.download(path: String, file: Path): Long {
  Files.createDirectories(file.parent)

  return connection.stream { stream, result ->
    stream.pipeline().addLast(FileDownloadHandler(result = result, file = file))
    stream.writeHeaders(createHeaders(HttpMethod.GET, AsciiString.of(path)), endStream = true)
  }
}

internal suspend fun Http2ClientConnection.download(
  path: CharSequence,
  file: Path,
  zstdDecompressContextPool: ZstdDecompressContextPool,
  digestFactory: (() -> MessageDigest)? = null,
  unzip: Boolean = false,
  ignoreNotFound: Boolean = false,
): DownloadResult {
  Files.createDirectories(file.parent)

  return connection.stream { stream, result ->
    // In Netty HTTP2, all streams use the same single-threaded event loop as we only use one channel.
    // Therefore, we must explicitly specify a different event loop group for execution.
    val group = (coroutineContext[ContinuationInterceptor] as EventLoopCoroutineDispatcher).eventLoop
    stream.pipeline().addLast(
      group,
      "zstdDecompressingFileDownloadHandler",
      ZstdDecompressingFileDownloadHandler(
        urlPath = path,
        result = result,
        downloadResult = DownloadResult(size = 0, digest = digestFactory?.invoke()),
        file = file,
        zstdDecompressContextPool = zstdDecompressContextPool,
        unzip = unzip,
        ignoreNotFound = ignoreNotFound,
      ),
    )

    stream.writeHeaders(createHeaders(HttpMethod.GET, AsciiString.of(path)), endStream = true)
  }
}

private class FileDownloadHandler(
  private val result: CompletableDeferred<Long>,
  private val file: Path,
) : InboundHandlerResultTracker<Http2StreamFrame>(result) {
  private var offset = 0L
  private var fileChannel: FileChannel? = null

  override fun acceptInboundMessage(message: Any): Boolean = message is Http2DataFrame || message is Http2HeadersFrame

  override fun handlerAdded(ctx: ChannelHandlerContext?) {
    fileChannel = FileChannel.open(file, OVERWRITE_OPERATION)
  }

  override fun handlerRemoved(context: ChannelHandlerContext) {
    fileChannel?.close()
    fileChannel = null
  }

  override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
    if (frame is Http2HeadersFrame) {
      val status = HttpResponseStatus.parseLine(frame.headers().status())
      if (status != HttpResponseStatus.OK) {
        if (status == HttpResponseStatus.NOT_FOUND) {
          result.complete(-1)
        }
        else {
          result.completeExceptionally(UnexpectedHttpStatus(null, status))
        }
      }
    }
    else if (frame is Http2DataFrame) {
      val content = frame.content()
      val fileChannel = fileChannel!!
      while (true) {
        val readableBytes = content.readableBytes()
        if (readableBytes == 0) {
          break
        }
        offset += content.readBytes(fileChannel, offset, readableBytes)
      }
      if (frame.isEndStream) {
        result.complete(offset)
      }
    }
  }
}
