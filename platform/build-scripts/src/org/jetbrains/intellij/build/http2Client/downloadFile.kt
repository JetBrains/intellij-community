// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.ZstdDecompressCtx
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
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
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.*

internal const val MAX_BUFFER_SIZE = 4 * 1014 * 1024
private val OVERWRITE_OPERATION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

internal data class DownloadResult(@JvmField var size: Long, @JvmField val digest: MessageDigest)

internal suspend fun Http2ClientConnection.download(path: String, file: Path): Long {
  Files.createDirectories(file.parent)

  return connection.stream { stream, result ->
    stream.pipeline().addLast(FileDownloadHandler(result = result, file = file))

    stream.writeHeaders(createHeaders(HttpMethod.GET, AsciiString.of(path)), endStream = true)
  }
}

internal suspend fun Http2ClientConnection.download(
  path: String,
  file: Path,
  zstdDecompressContextPool: ZstdDecompressContextPool,
  digestFactory: () -> MessageDigest,
): DownloadResult {
  Files.createDirectories(file.parent)

  return connection.stream { stream, result ->
    stream.pipeline().addLast(
      ZstdDecompressingFileDownloadHandler(
        result = result,
        downloadResult = DownloadResult(size = 0, digest = digestFactory()),
        file = file,
        zstdDecompressContextPool = zstdDecompressContextPool,
      ),
    )

    stream.writeHeaders(createHeaders(HttpMethod.GET, AsciiString.of(path)), endStream = true)
  }
}

private class ZstdDecompressingFileDownloadHandler(
  private val result: CompletableDeferred<DownloadResult>,
  private val downloadResult: DownloadResult,
  private val file: Path,
  private val zstdDecompressContextPool: ZstdDecompressContextPool,
) : InboundHandlerResultTracker<Http2StreamFrame>(result) {
  private var offset = 0L
  private var fileChannel: FileChannel? = null
  private var zstdDecompressContext: ZstdDecompressCtx? = null

  override fun acceptInboundMessage(message: Any): Boolean = message is Http2DataFrame || message is Http2HeadersFrame

  override fun handlerAdded(ctx: ChannelHandlerContext?) {
    fileChannel = FileChannel.open(file, OVERWRITE_OPERATION)
    zstdDecompressContext = zstdDecompressContextPool.allocate()
  }

  override fun handlerRemoved(context: ChannelHandlerContext) {
    try {
      fileChannel?.close()
      fileChannel = null
    }
    finally {
      zstdDecompressContext?.let {
        zstdDecompressContext = null
        zstdDecompressContextPool.release(it)
      }
    }
  }

  override fun channelRead0(context: ChannelHandlerContext, frame: Http2StreamFrame) {
    if (frame is Http2HeadersFrame) {
      val status = HttpResponseStatus.parseLine(frame.headers().status())
      if (status != HttpResponseStatus.OK) {
        result.completeExceptionally(UnexpectedHttpStatus(null, status))
      }
    }
    else if (frame is Http2DataFrame) {
      val content = frame.content()
      downloadResult.size += content.readableBytes()
      writeChunk(content, context.alloc())

      if (frame.isEndStream) {
        result.complete(downloadResult)
      }
    }
  }

  private fun writeChunk(chunk: ByteBuf, allocator: ByteBufAllocator) {
    val sourceBuffer = chunk.nioBuffer()
    val zstdDecompressContext = zstdDecompressContext!!
    val fileChannel = fileChannel!!
    do {
      val targetNettyBuffer = allocator.directBuffer((sourceBuffer.remaining() * 4).coerceAtMost(MAX_BUFFER_SIZE))
      try {
        val targetBuffer = targetNettyBuffer.nioBuffer(0, targetNettyBuffer.capacity())
        zstdDecompressContext.decompressDirectByteBufferStream(targetBuffer, sourceBuffer)

        targetBuffer.flip()
        if (targetBuffer.hasRemaining()) {
          targetBuffer.mark()
          downloadResult.digest.update(targetBuffer)
          targetBuffer.reset()

          do {
            offset += fileChannel.write(targetBuffer, offset)
          }
          while (targetBuffer.hasRemaining())
        }
      }
      finally {
        targetNettyBuffer.release()
      }
    }
    while (sourceBuffer.hasRemaining())
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
