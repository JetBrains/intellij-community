// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode", "SSBasedInspection")

package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdCompressCtx
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2StreamChannel
import io.netty.util.AsciiString
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.intellij.build.io.unmapBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.EnumSet
import kotlin.math.min

private const val SOURCE_BUFFER_SIZE = 8 * 1024 * 1024

internal val READ_OPERATION = EnumSet.of(StandardOpenOption.READ)

internal class UploadResult {
  @JvmField var uploadedSize: Long = 0L
  @JvmField var fileSize: Long = 0L
}

internal suspend fun Http2ClientConnection.upload(path: CharSequence, file: Path): UploadResult {
  return connection.stream { stream, result ->
    val uploadResult = UploadResult()
    stream.pipeline().addLast(WebDavPutStatusChecker(result, uploadResult, path))

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM), endStream = false)

    FileChannel.open(file, READ_OPERATION).use { channel ->
      uploadUncompressed(fileChannel = channel, maxSourceBlockSize = SOURCE_BUFFER_SIZE, stream = stream, fileSize = channel.size(), result = uploadResult)
    }
  }
}

internal suspend fun Http2ClientConnection.upload(path: CharSequence, data: CharSequence) {
  return connection.stream { stream, result ->
    stream.pipeline().addLast(WebDavPutStatusChecker(result, Unit, path))

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM), endStream = false)
    stream.writeAndFlush(DefaultHttp2DataFrame(ByteBufUtil.writeUtf8(stream.alloc(), data), true)).joinCancellable()
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
    val uploadResult = UploadResult()
    stream.pipeline().addLast(WebDavPutStatusChecker(result, uploadResult, path))

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM), endStream = false)

    if (isDir) {
      zstdCompressContextPool.withZstd(contentSize = -1) { zstd ->
        compressAndUploadDir(dir = file, zstd = zstd, stream = stream, sourceBlockSize = sourceBlockSize, result = uploadResult)
      }
    }
    else {
      compressFile(file = file, zstdCompressContextPool = zstdCompressContextPool, sourceBlockSize = sourceBlockSize, stream = stream, result = uploadResult)
    }
  }
}

private suspend fun compressFile(
  file: Path,
  zstdCompressContextPool: ZstdCompressContextPool,
  sourceBlockSize: Int,
  stream: Http2StreamChannel,
  result: UploadResult,
) {
  val fileSize: Long
  val fileBuffer = FileChannel.open(file, READ_OPERATION).use { channel ->
    fileSize = channel.size()
    channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
  }
  try {
    zstdCompressContextPool.withZstd(fileSize) { zstd ->
      compressAndUploadFile(fileBuffer = fileBuffer, maxSourceBlockSize = sourceBlockSize, zstd = zstd, stream = stream, fileSize = fileSize, result = result)
    }
  }
  finally {
    unmapBuffer(fileBuffer)
  }
}

private class WebDavPutStatusChecker<T : Any>(
  private val result: CompletableDeferred<T>,
  private val resultObject: T,
  private val path: CharSequence,
) : ChannelInboundHandlerAdapter() {
  override fun exceptionCaught(context: ChannelHandlerContext, cause: Throwable) {
    result.completeExceptionally(cause)
  }

  override fun channelRead(ctx: ChannelHandlerContext, message: Any) {
    if (message is Http2HeadersFrame) {
      try {
        handleFrame(message)
      }
      finally {
        ReferenceCountUtil.release(message)
      }
    }
    else {
      ctx.fireChannelRead(message)
    }
  }

  private fun handleFrame(frame: Http2HeadersFrame) {
    val status = frame.headers().status()?.let { HttpResponseStatus.parseLine(it) }
    if (!frame.isEndStream) {
      if (status != null && status.code() >= 400) {
        result.completeExceptionally(UnexpectedHttpStatus(urlPath = path, status = status))
      }
      return
    }

    // WebDAV server returns 204 for existing resources
    if (status == HttpResponseStatus.CREATED || status == HttpResponseStatus.NO_CONTENT || status == HttpResponseStatus.OK) {
      result.complete(resultObject)
    }
    else {
      result.completeExceptionally(UnexpectedHttpStatus(urlPath = path, status = status!!))
    }
  }
}

private suspend fun compressAndUploadFile(
  fileBuffer: MappedByteBuffer,
  maxSourceBlockSize: Int,
  zstd: ZstdCompressCtx,
  stream: Http2StreamChannel,
  fileSize: Long,
  result: UploadResult,
) {
  var position = 0
  result.fileSize = fileSize
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
    result.uploadedSize += compressedSize

    //println("Uploaded $chunkSize bytes from $fileSize (compressed: ${result.uploadedSize})")
    val endStream = position >= fileSize
    stream.writeAndFlush(DefaultHttp2DataFrame(target, endStream)).joinCancellable()
    if (endStream) {
      break
    }
  }
}

private suspend fun uploadUncompressed(fileChannel: FileChannel, maxSourceBlockSize: Int, stream: Http2StreamChannel, fileSize: Long, result: UploadResult) {
  result.fileSize = fileSize
  var position = 0L
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
    result.uploadedSize += chunkSize

    val endStream = position >= fileSize
    stream.writeAndFlush(DefaultHttp2DataFrame(target, endStream)).joinCancellable()
    if (endStream) {
      break
    }
  }
}