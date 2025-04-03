// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode", "SSBasedInspection")

package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdCompressCtx
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.util.AsciiString
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.intellij.build.io.unmapBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.EnumSet
import kotlin.math.min

private const val SOURCE_BUFFER_SIZE = 4 * 1024 * 1024

internal val READ_OPERATION = EnumSet.of(StandardOpenOption.READ)

internal data class UploadResult(@JvmField var uploadedSize: Long, @JvmField var fileSize: Long)

internal suspend fun Http2ClientConnection.upload(path: CharSequence, file: Path): UploadResult {
  return connection.stream { stream, result ->
    val status = CompletableDeferred<Unit>(parent = result)
    stream.pipeline().addLast(WebDavPutStatusChecker(status))

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM), endStream = false)

    val uploadResult = FileChannel.open(file, READ_OPERATION).use { channel ->
      uploadUncompressed(fileChannel = channel, maxSourceBlockSize = SOURCE_BUFFER_SIZE, stream = stream, fileSize = channel.size())
    }

    status.await()
    result.complete(uploadResult)
  }
}

internal suspend fun Http2ClientConnection.upload(path: CharSequence, data: CharSequence) {
  return connection.stream { stream, result ->
    stream.pipeline().addLast(WebDavPutStatusChecker(result))

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM), endStream = false)
    stream.writeData(ByteBufUtil.writeUtf8(stream.alloc(), data), endStream = true)
  }
}

internal suspend fun Http2ClientConnection.upload(
  path: CharSequence,
  file: Path,
  sourceBlockSize: Int = SOURCE_BUFFER_SIZE,
  zstdCompressContextPool: ZstdCompressContextPool,
  isDir: Boolean = false,
): UploadResult {
  return connection.stream { stream, result ->
    val status = CompletableDeferred<Unit>(parent = result)
    stream.pipeline().addLast(WebDavPutStatusChecker(status))

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM), endStream = false)

    val uploadResult = if (isDir) {
      zstdCompressContextPool.withZstd(contentSize = -1) { zstd ->
        compressAndUploadDir(dir = file, zstd = zstd, stream = stream, sourceBlockSize = sourceBlockSize)
      }
    }
    else {
      compressFile(file = file, zstdCompressContextPool = zstdCompressContextPool, sourceBlockSize = sourceBlockSize, stream = stream)
    }

    status.await()
    result.complete(uploadResult)
  }
}

private suspend fun compressFile(file: Path, zstdCompressContextPool: ZstdCompressContextPool, sourceBlockSize: Int, stream: Http2StreamChannel): UploadResult {
  val fileSize: Long
  val fileBuffer = FileChannel.open(file, READ_OPERATION).use { channel ->
    fileSize = channel.size()
    channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
  }
  try {
    return zstdCompressContextPool.withZstd(fileSize) { zstd ->
      compressAndUploadFile(fileBuffer = fileBuffer, maxSourceBlockSize = sourceBlockSize, zstd = zstd, stream = stream, fileSize = fileSize)
    }
  }
  finally {
    unmapBuffer(fileBuffer)
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
      result.completeExceptionally(UnexpectedHttpStatus(urlPath = null, status = status))
    }
  }
}

private suspend fun compressAndUploadFile(
  fileBuffer: MappedByteBuffer,
  maxSourceBlockSize: Int,
  zstd: ZstdCompressCtx,
  stream: Http2StreamChannel,
  fileSize: Long,
): UploadResult {
  var position = 0
  var uploadedSize = 0L
  while (true) {
    val chunkSize = min(fileSize - position, maxSourceBlockSize.toLong()).toInt()
    val targetSize = Zstd.compressBound(chunkSize.toLong()).toInt()
    val target = stream.alloc().directBuffer(targetSize)
    val targetNio = target.internalNioBuffer(target.writerIndex(), targetSize)
    val compressedSize = zstd.compressDirectByteBuffer(
      targetNio, // compress into targetBuffer
      targetNio.position(), // write compressed data starting at offset position()
      targetSize, // write no more than target block size bytes
      fileBuffer, // read data to compress from fileBuffer
      position, // start reading at position()
      chunkSize, // read chunk size bytes
    )
    assert(compressedSize > 0)
    target.writerIndex(target.writerIndex() + compressedSize)
    assert(target.readableBytes() == compressedSize)

    position += chunkSize
    uploadedSize += compressedSize

    val endStream = position >= fileSize
    stream.writeAndFlush(DefaultHttp2DataFrame(target, endStream)).joinCancellable()
    if (endStream) {
      return UploadResult(uploadedSize = uploadedSize, fileSize = fileSize)
    }
  }
}

private suspend fun uploadUncompressed(fileChannel: FileChannel, maxSourceBlockSize: Int, stream: Http2StreamChannel, fileSize: Long): UploadResult {
  var position = 0L
  var uploadedSize = 0L
  while (true) {
    val chunkSize = min(fileSize - position, maxSourceBlockSize.toLong()).toInt()
    val target = stream.alloc().directBuffer(chunkSize)

    var written = 0
    while (written != chunkSize) {
      val n = target.writeBytes(fileChannel, position, chunkSize)
      if (n == -1) {
        target.release()
        throw RuntimeException("unexpected EOF")
      }
      written += n
    }

    position += chunkSize
    uploadedSize += chunkSize

    val endStream = position >= fileSize
    stream.writeAndFlush(DefaultHttp2DataFrame(target, endStream)).joinCancellable()
    if (endStream) {
      return UploadResult(uploadedSize = uploadedSize, fileSize = fileSize)
    }
  }
}