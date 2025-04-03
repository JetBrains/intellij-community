// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.intellij.build.http2Client

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdCompressCtx
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.Http2StreamChannel
import org.jetbrains.intellij.build.io.ZipIndexWriter
import org.jetbrains.intellij.build.io.archiveDir
import org.jetbrains.intellij.build.io.writeZipLocalFileHeader
import java.io.EOFException
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

internal suspend fun compressAndUploadDir(dir: Path, zstd: ZstdCompressCtx, stream: Http2StreamChannel, sourceBlockSize: Int): UploadResult {
  val localPrefixLength = dir.toString().length + 1
  val zipIndexWriter = ZipIndexWriter(null)
  try {
    var uncompressedPosition = 0L
    var uploadedSize = 0L
    val sourceBufferRef = AtomicReference<ByteBuf>(null)
    try {
      @Synchronized
      fun reallocateSourceBuffer(): ByteBuf {
        val sourceBuffer = stream.alloc().directBuffer(sourceBlockSize)
        if (!sourceBufferRef.compareAndSet(null, sourceBuffer)) {
          sourceBuffer.release()
          throw IllegalStateException("Concurrent use of source is prohibited")
        }
        return sourceBuffer
      }

      archiveDir(startDir = dir, addFile = { file ->
        val name = file.toString().substring(localPrefixLength).replace(File.separatorChar, '/').toByteArray()

        // We use sourceBuffer to compress in blocks of at least 4MB to ensure optimal compression.
        // For large files that exceed the size of the source buffer, we could avoid using an intermediate buffer and compress the mapped file directly.
        // However, this optimization has not been implemented yet.
        // Most files are small, so this approach is generally efficient.
        FileChannel.open(file, READ_OPERATION).use { channel ->
          assert(channel.size() <= Int.MAX_VALUE)
          val size = channel.size().toInt()
          val relativeOffsetOfLocalFileHeader = uncompressedPosition

          val headerSize = 30 + name.size
          val requiredSize = headerSize + size

          // make sure that we can write the file header
          var sourceBuffer = sourceBufferRef.get()
          if (sourceBuffer != null && sourceBuffer.writableBytes() < headerSize) {
            uploadedSize += compressBufferAndWrite(sourceBuffer = sourceBuffer, stream = stream, zstd = zstd, endStream = false)
            sourceBuffer = null
          }

          if (sourceBuffer == null) {
            sourceBuffer = reallocateSourceBuffer()
          }

          writeZipLocalFileHeader(path = name, size = size, crc32 = 0, buffer = sourceBuffer)
          zipIndexWriter.writeCentralFileHeader(path = name, size = size, crc = 0, headerOffset = relativeOffsetOfLocalFileHeader)

          uncompressedPosition += requiredSize

          var toRead = size
          var readPosition = 0L
          while (toRead > 0) {
            if (!sourceBuffer.isWritable) {
              // flush
              uploadedSize += compressBufferAndWrite(sourceBuffer = sourceBuffer, stream = stream, zstd = zstd, endStream = false)
              sourceBuffer = reallocateSourceBuffer()
            }

            val chunkSize = min(toRead, sourceBuffer.writableBytes())
            val n = sourceBuffer.writeBytes(channel, readPosition, chunkSize)
            if (n == -1) {
              throw EOFException()
            }

            toRead -= n
            readPosition += n
          }
        }
      })

      val zipIndexData = zipIndexWriter.finish(centralDirectoryOffset = uncompressedPosition, indexDataEnd = -1)
      var toRead = zipIndexData.readableBytes()
      uncompressedPosition += toRead

      // compress central directory not in a separate ZSTD frame if possible
      sourceBufferRef.get()?.let { sourceBuffer ->
        val chunkSize = min(toRead, sourceBuffer.writableBytes())
        if (chunkSize > 0) {
          sourceBuffer.writeBytes(zipIndexData, chunkSize)
          toRead -= chunkSize

          uploadedSize += compressBufferAndWrite(sourceBuffer = sourceBuffer, stream = stream, zstd = zstd, endStream = toRead == 0)
        }
        else {
          sourceBuffer.release()
          sourceBufferRef.compareAndSet(sourceBuffer, null)
        }
      }

      if (toRead > 0) {
        uploadedSize += compressBufferAndWrite(sourceBuffer = zipIndexData, stream = stream, zstd = zstd, endStream = true, releaseSource = false)
      }
    }
    finally {
      sourceBufferRef.get()?.release()
    }

    return UploadResult(uploadedSize = uploadedSize, fileSize = uncompressedPosition)
  }
  finally {
    zipIndexWriter.release()
  }
}

private suspend fun compressBufferAndWrite(sourceBuffer: ByteBuf, stream: Http2StreamChannel, zstd: ZstdCompressCtx, endStream: Boolean, releaseSource: Boolean = true): Int {
  val chunkSize = sourceBuffer.readableBytes()
  val targetSize = Zstd.compressBound(chunkSize.toLong()).toInt()
  var target = stream.alloc().directBuffer(targetSize)
  try {
    val targetNio = target.internalNioBuffer(target.writerIndex(), targetSize)
    val sourceNio = sourceBuffer.internalNioBuffer(sourceBuffer.readerIndex(), sourceBuffer.readableBytes())
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

    if (releaseSource) {
      sourceBuffer.release()
    }

    val future = stream.writeAndFlush(DefaultHttp2DataFrame(target, endStream))
    target = null
    future.joinCancellable()
    return compressedSize
  }
  finally {
    target?.release()
  }
}
