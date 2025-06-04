// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode", "SSBasedInspection")

package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdCompressCtx
import io.netty.buffer.ByteBuf
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
import org.jetbrains.intellij.build.io.*
import java.io.EOFException
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.ZipEntry
import kotlin.math.min

internal val READ_OPERATION = EnumSet.of(StandardOpenOption.READ)

internal data class UploadResult(@JvmField var uploadedSize: Long, @JvmField var fileSize: Long)

internal suspend fun Http2ClientConnection.upload(path: CharSequence, file: Path, extraHeaders: List<AsciiString> = emptyList()): UploadResult {
  return upload(path = path, file = file, sourceBlockSize = 1024 * 1024, zstdCompressContextPool = null, extraHeaders = extraHeaders)
}

internal suspend fun Http2ClientConnection.upload(path: CharSequence, data: CharSequence, extraHeaders: List<AsciiString> = emptyList()) {
  val headers = withDefaultContentType(extraHeaders).toTypedArray()
  return connection.stream { stream, result ->
    stream.pipeline().addLast(WebDavPutStatusChecker(result))

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), *headers), endStream = false)
    stream.writeData(ByteBufUtil.writeUtf8(stream.alloc(), data), endStream = true)
  }
}

internal suspend fun Http2ClientConnection.upload(
  path: CharSequence,
  file: Path,
  sourceBlockSize: Int = 4 * 1024 * 1024,
  zstdCompressContextPool: ZstdCompressContextPool?,
  isDir: Boolean = false,
  extraHeaders: List<AsciiString> = emptyList(),
): UploadResult {
  val headers = withDefaultContentType(extraHeaders).toTypedArray()
  return connection.stream { stream, result ->
    val status = CompletableDeferred<Unit>(parent = result)
    stream.pipeline().addLast(WebDavPutStatusChecker(status))

    stream.writeHeaders(createHeaders(HttpMethod.PUT, AsciiString.of(path), *headers), endStream = false)

    val uploadResult = if (zstdCompressContextPool == null) {
      FileChannel.open(file, READ_OPERATION).use { channel ->
        uploadUncompressed(fileChannel = channel, sourceBlockSize = sourceBlockSize, stream = stream, fileSize = channel.size())
      }
    }
    else if (isDir) {
      zstdCompressContextPool.withZstd(contentSize = -1) { zstd ->
        stream.alloc().directBuffer(sourceBlockSize).use { sourceBuffer ->
          compressDir(
            dir = file,
            zstd = zstd,
            sourceBuffer = sourceBuffer,
            stream = stream,
          )
        }
      }
    }
    else {
      compressFile(file = file, zstdCompressContextPool = zstdCompressContextPool, sourceBlockSize = sourceBlockSize, stream = stream)
    }

    status.await()
    result.complete(uploadResult)
  }
}

private fun withDefaultContentType(extraHeaders: List<AsciiString>): ArrayList<AsciiString> {
  require(extraHeaders.size % 2 == 0) {
    "extraHeaders must be a list of key-value pairs, got $extraHeaders"
  }
  val headers: ArrayList<AsciiString>
  if (extraHeaders.contains(HttpHeaderNames.CONTENT_TYPE)) {
    headers = arrayListOf()
  }
  else {
    headers = arrayListOf(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
  }
  headers.addAll(extraHeaders)
  return headers
}

private suspend fun compressFile(
  file: Path,
  zstdCompressContextPool: ZstdCompressContextPool,
  sourceBlockSize: Int,
  stream: Http2StreamChannel,
): UploadResult {
  val fileSize: Long
  val fileBuffer = FileChannel.open(file, READ_OPERATION).use { channel ->
    fileSize = channel.size()
    channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize)
  }
  try {
    return zstdCompressContextPool.withZstd(fileSize) { zstd ->
      compressAndUpload(
        fileBuffer = fileBuffer,
        sourceBlockSize = sourceBlockSize,
        zstd = zstd,
        stream = stream,
        fileSize = fileSize,
      )
    }
  }
  finally {
    unmapBuffer(fileBuffer)
  }
}

private suspend fun compressDir(
  dir: Path,
  zstd: ZstdCompressCtx,
  stream: Http2StreamChannel,
  sourceBuffer: ByteBuf,
): UploadResult {
  val localPrefixLength = dir.toString().length + 1
  val zipIndexWriter = ZipIndexWriter(indexWriter = null)
  try {
    var uncompressedPosition = 0L
    var uploadedSize = 0L
    archiveDir(startDir = dir, addFile = { file ->
      val name = file.toString().substring(localPrefixLength).replace(File.separatorChar, '/').toByteArray()

      // make sure that we can write the file header
      if (sourceBuffer.writableBytes() < (30 + name.size)) {
        uploadedSize += compressBufferAndWrite(source = sourceBuffer, stream = stream, zstd = zstd, endStream = false)
      }

      // We use sourceBuffer to compress in blocks of at least 4MB to ensure optimal compression.
      // For large files that exceed the size of the source buffer, we could avoid using an intermediate buffer and compress the mapped file directly.
      // However, this optimization has not been implemented yet.
      // Most files are small, so this approach is generally efficient.
      FileChannel.open(file, EnumSet.of(StandardOpenOption.READ)).use { channel ->
        assert(channel.size() <= Int.MAX_VALUE)
        val size = channel.size().toInt()
        val relativeOffsetOfLocalFileHeader = uncompressedPosition
        writeZipLocalFileHeader(name = name, size = size, compressedSize = size, crc32 = 0, method = ZipEntry.STORED, buffer = sourceBuffer)
        zipIndexWriter.writeCentralFileHeader(
          size = size,
          compressedSize = size,
          method = ZipEntry.STORED,
          name = name,
          crc = 0,
          localFileHeaderOffset = relativeOffsetOfLocalFileHeader,
          dataOffset = -1,
        )

        uncompressedPosition += 30 + name.size + size

        var toRead = size
        var readPosition = 0L
        var writableBytes = sourceBuffer.writableBytes()
        while (toRead > 0) {
          if (writableBytes <= 0) {
            // flush
            uploadedSize += compressBufferAndWrite(source = sourceBuffer, stream = stream, zstd = zstd, endStream = false)
            writableBytes = sourceBuffer.writableBytes()
          }

          val chunkSize = min(toRead, writableBytes)
          val n = sourceBuffer.writeBytes(channel, readPosition, chunkSize)
          if (n == -1) {
            throw EOFException()
          }

          toRead -= n
          readPosition += n
          writableBytes -= n
        }
      }
    })

    zipIndexWriter.finish(centralDirectoryOffset = uncompressedPosition, indexWriter = null, indexOffset = -1)

    var toRead = zipIndexWriter.buffer.readableBytes()
    uncompressedPosition += toRead

    // compress central directory not in a separate ZSTD frame if possible
    var writableBytes = sourceBuffer.writableBytes()
    if (writableBytes > 0) {
      val chunkSize = min(toRead, writableBytes)
      sourceBuffer.writeBytes(zipIndexWriter.buffer, chunkSize)
      toRead -= chunkSize

      uploadedSize += compressBufferAndWrite(source = sourceBuffer, stream = stream, zstd = zstd, endStream = toRead == 0)
    }

    if (toRead > 0) {
      uploadedSize += compressBufferAndWrite(source = zipIndexWriter.buffer, stream = stream, zstd = zstd, endStream = true)
    }

    return UploadResult(uploadedSize = uploadedSize, fileSize = uncompressedPosition)
  }
  finally {
    zipIndexWriter.release()
  }
}

private suspend fun compressBufferAndWrite(source: ByteBuf, stream: Http2StreamChannel, zstd: ZstdCompressCtx, endStream: Boolean): Int {
  val chunkSize = source.readableBytes()
  val targetSize = Zstd.compressBound(chunkSize.toLong()).toInt()
  var target = stream.alloc().directBuffer(targetSize)
  try {
    val targetNio = target.internalNioBuffer(0, targetSize)
    val sourceNio = source.internalNioBuffer(source.readerIndex(), source.readableBytes())
    val compressedSize = zstd.compressDirectByteBuffer(
      targetNio, // compress into targetBuffer
      targetNio.position(), // write compressed data starting at offset position()
      targetSize, // write no more than target block size bytes
      sourceNio, // read data to compress from source
      sourceNio.position(), // start reading at position()
      chunkSize, // read chunk size bytes
    )
    assert(compressedSize > 0)
    target.writerIndex(compressedSize)
    assert(target.readableBytes() == compressedSize)

    stream.writeAndFlush(DefaultHttp2DataFrame(target, endStream)).also { target = null }.joinCancellable()
    return compressedSize
  }
  finally {
    target?.release()
    source.clear()
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

private suspend fun compressAndUpload(
  fileBuffer: MappedByteBuffer,
  sourceBlockSize: Int,
  zstd: ZstdCompressCtx,
  stream: Http2StreamChannel,
  fileSize: Long,
): UploadResult {
  var position = 0
  var uploadedSize = 0L
  while (true) {
    val chunkSize = min(fileSize - position, sourceBlockSize.toLong()).toInt()
    val targetSize = Zstd.compressBound(chunkSize.toLong()).toInt()
    val target = stream.alloc().directBuffer(targetSize)
    val targetNio = target.internalNioBuffer(0, targetSize)
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

private suspend fun uploadUncompressed(
  fileChannel: FileChannel,
  sourceBlockSize: Int,
  stream: Http2StreamChannel,
  fileSize: Long,
): UploadResult {
  var position = 0L
  var uploadedSize = 0L
  while (true) {
    val chunkSize = min(fileSize - position, sourceBlockSize.toLong()).toInt()
    val target = stream.alloc().directBuffer(chunkSize)

    var written = 0
    while (written != chunkSize) {
      val n = target.writeBytes(fileChannel, position, chunkSize)
      if (n == -1) {
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