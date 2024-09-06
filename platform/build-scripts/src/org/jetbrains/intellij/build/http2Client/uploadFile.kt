// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode")

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
import java.util.*
import kotlin.math.min

internal val READ_OPERATION = EnumSet.of(StandardOpenOption.READ)

internal data class UploadResult(@JvmField var uploadedSize: Long, @JvmField var fileSize: Long)

internal suspend fun Http2ClientConnection.upload(path: CharSequence, file: Path): UploadResult {
  return upload(path = path, file = file, sourceBlockSize = 1024 * 1024, zstdCompressContextPool = null)
}

internal suspend fun Http2ClientConnection.upload(path: CharSequence, data: CharSequence) {
  return connection.stream { stream, result ->
    val handler = WebDavPutStatusChecker(result)
    handler.uploadedResult = Unit
    stream.pipeline().addLast(handler)

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM), endStream = false)
    stream.writeData(ByteBufUtil.writeUtf8(stream.alloc(), data), endStream = true)
  }
}

internal suspend fun Http2ClientConnection.upload(
  path: CharSequence,
  file: Path,
  sourceBlockSize: Int,
  zstdCompressContextPool: ZstdCompressContextPool?,
): UploadResult {
  return connection.stream { stream, result ->
    val handler = WebDavPutStatusChecker(result)
    stream.pipeline().addLast(handler)

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM), endStream = false)

    if (zstdCompressContextPool == null) {
      FileChannel.open(file, READ_OPERATION).use { channel ->
        uploadUncompressed(fileChannel = channel, sourceBlockSize = sourceBlockSize, stream = stream, fileSize = channel.size()) {
          handler.uploadedResult = it
        }
      }
    }
    else {
      val fileBuffer = FileChannel.open(file, READ_OPERATION).use { channel ->
        channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
      }
      try {
        val fileSize = fileBuffer.remaining().toLong()

        zstdCompressContextPool.withZstd(fileSize) { zstd ->
          compressAndUpload(
            fileBuffer = fileBuffer,
            sourceBlockSize = sourceBlockSize,
            zstd = zstd,
            stream = stream,
            fileSize = fileSize,
          ) {
            handler.uploadedResult = it
          }
        }
      }
      finally {
        unmapBuffer(fileBuffer)
      }
    }

    // 1. writer must send the last data frame with endStream=true
    // 2. stream now has the half-closed state - we listen for server header response with endStream
    // 3. our ChannelInboundHandler above checks status and Netty closes the stream (as endStream was sent by both client and server)
  }
}

private class WebDavPutStatusChecker<T>(private val result: CompletableDeferred<T>) : InboundHandlerResultTracker<Http2HeadersFrame>(result) {
  @JvmField
  var uploadedResult: T? = null

  override fun channelRead0(context: ChannelHandlerContext, frame: Http2HeadersFrame) {
    if (!frame.isEndStream) {
      return
    }

    val status = HttpResponseStatus.parseLine(frame.headers().status())
    // WebDAV server returns 204 for existing resources
    if (status == HttpResponseStatus.CREATED || status == HttpResponseStatus.NO_CONTENT || status == HttpResponseStatus.OK) {
      result.complete(uploadedResult!!)
    }
    else {
      result.completeExceptionally(UnexpectedHttpStatus(null, status))
    }
  }
}

private suspend fun compressAndUpload(
  fileBuffer: MappedByteBuffer,
  sourceBlockSize: Int,
  zstd: ZstdCompressCtx,
  stream: Http2StreamChannel,
  fileSize: Long,
  uploadResultConsumer: (UploadResult) -> Unit,
) {
  var position = 0
  var uploadedSize = 0L
  while (true) {
    val chunkSize = min(fileSize - position, sourceBlockSize.toLong()).toInt()
    val targetSize = Zstd.compressBound(chunkSize.toLong()).toInt()
    val targetNettyBuffer = stream.alloc().directBuffer(targetSize)
    val targetBuffer = targetNettyBuffer.nioBuffer(0, targetSize)
    val compressedSize = zstd.compressDirectByteBuffer(
      targetBuffer, // compress into targetBuffer
      targetBuffer.position(), // write compressed data starting at offset position()
      targetSize, // write no more than target block size bytes
      fileBuffer, // read data to compress from fileBuffer
      position, // start reading at position()
      chunkSize, // read chunk size bytes
    )
    assert(compressedSize > 0)
    targetNettyBuffer.writerIndex(targetNettyBuffer.writerIndex() + compressedSize)
    assert(targetNettyBuffer.readableBytes() == compressedSize)

    position += chunkSize
    uploadedSize += compressedSize

    val endStream = position >= fileSize
    val writeFuture = stream.writeAndFlush(DefaultHttp2DataFrame(targetNettyBuffer, endStream))
    if (endStream) {
      uploadResultConsumer(UploadResult(uploadedSize = uploadedSize, fileSize = fileSize))
    }
    writeFuture.joinCancellable()

    if (endStream) {
      return
    }
  }
}

private suspend fun uploadUncompressed(
  fileChannel: FileChannel,
  sourceBlockSize: Int,
  stream: Http2StreamChannel,
  fileSize: Long,
  uploadResultConsumer: (UploadResult) -> Unit,
) {
  var position = 0L
  var uploadedSize = 0L
  while (true) {
    val chunkSize = min(fileSize - position, sourceBlockSize.toLong()).toInt()
    val targetNettyBuffer = stream.alloc().directBuffer(chunkSize)

    var written = 0
    while (written != chunkSize) {
      val n = targetNettyBuffer.writeBytes(fileChannel, position, chunkSize)
      if (n == -1) {
        throw RuntimeException("unexpected EOF")
      }
      written += n
    }

    position += chunkSize
    uploadedSize += chunkSize

    val endStream = position >= fileSize
    val writeFuture = stream.writeAndFlush(DefaultHttp2DataFrame(targetNettyBuffer, endStream))
    if (endStream) {
      uploadResultConsumer(UploadResult(uploadedSize = uploadedSize, fileSize = fileSize))
    }
    writeFuture.joinCancellable()

    if (endStream) {
      return
    }
  }
}