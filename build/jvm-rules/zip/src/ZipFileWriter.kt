// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName", "DuplicatedCode")

package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.channels.GatheringByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import kotlin.math.min

val W_CREATE_NEW: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
@PublishedApi
internal val WRITE: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.WRITE)
private val READ = EnumSet.of(StandardOpenOption.READ)

// 1 MB
private const val largeFileThreshold = 1_048_576
private const val compressThreshold = 8 * 1024
// 8 MB (as JDK)
private const val mappedTransferSize = 8L * 1024L * 1024L

@Suppress("DuplicatedCode")
fun ZipArchiveOutputStream.file(nameString: String, file: Path) {
  val name = nameString.toByteArray()
  FileChannel.open(file, READ).use { channel ->
    val size = channel.size()
    assert(size <= Int.MAX_VALUE)
    writeEntryHeaderWithoutCrc(name = name, size = size.toInt())
    transferFrom(channel, size)
  }
  return
}

fun transformZipUsingTempFile(file: Path, indexWriter: IkvIndexBuilder?, task: (ZipFileWriter) -> Unit) {
  val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
  try {
    ZipFileWriter(
      channel = FileChannel.open(tempFile, WRITE),
      zipIndexWriter = ZipIndexWriter(indexWriter),
    ).use {
      task(it)
    }
    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
  }
  finally {
    Files.deleteIfExists(tempFile)
  }
}

inline fun writeNewZipWithoutIndex(
  file: Path,
  compress: Boolean = false,
  task: (ZipFileWriter) -> Unit,
) {
  Files.createDirectories(file.parent)
  ZipFileWriter(
    channel = FileChannel.open(file, W_CREATE_NEW),
    deflater = if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null,
    zipIndexWriter = ZipIndexWriter(indexWriter = null),
  ).use {
    task(it)
  }
}

// you must pass SeekableByteChannel if files are written (`file` method)
class ZipFileWriter(
  channel: GatheringByteChannel,
  private val deflater: Deflater? = null,
  private val zipIndexWriter: ZipIndexWriter,
) : AutoCloseable {
  // size is written as part of optimized metadata - so, if compression is enabled, optimized metadata will be incorrect
  internal val resultStream = ZipArchiveOutputStream(channel = channel, zipIndexWriter = zipIndexWriter)
  private val crc32 = CRC32()

  private val deflateBufferAllocator = if (deflater == null) null else ByteBufferAllocator()

  val channelPosition: Long
    get() = resultStream.getChannelPosition()

  @Suppress("DuplicatedCode")
  fun file(nameString: String, file: Path) {
    var isCompressed = deflater != null && !nameString.endsWith(".png")

    val name = nameString.toByteArray()
    crc32.reset()

    val headerSize = 30 + name.size
    var input: ByteBuf? = null
    try {
      val size: Int
      FileChannel.open(file, READ).use { channel ->
        size = channel.size().toInt()
        if (size == 0) {
          resultStream.writeEmptyFile(name = name)
          return
        }

        if (size < compressThreshold) {
          isCompressed = false
        }

        if (size > largeFileThreshold || isCompressed) {
          val headerPosition = resultStream.getChannelPositionAndAdd(headerSize)
          var compressedSize = writeLargeFile(
            fileSize = size.toLong(),
            channel = channel,
            deflater = if (isCompressed) deflater else null,
          ).toInt()
          val crc = crc32.value
          val method = if (compressedSize == -1) {
            compressedSize = size
            ZipEntry.STORED
          }
          else {
            ZipEntry.DEFLATED
          }
          resultStream.writeEntryHeaderAt(
            name = name,
            position = headerPosition,
            size = size,
            compressedSize = compressedSize,
            crc = crc,
            method = method,
          )
          return
        }

        input = ByteBufAllocator.DEFAULT.directBuffer(headerSize + size)
        // set position to compute CRC
        input.writerIndex(headerSize)
        var toRead = size
        var position = 0L
        while (toRead > 0) {
          val n = input.writeBytes(channel, position, toRead)
          if (n == -1) {
            throw EOFException()
          }

          toRead -= n
          position += n
        }
        crc32.update(input.nioBuffer(headerSize, size))
      }

      val crc = crc32.value
      input!!.writerIndex(0)

      writeZipLocalFileHeader(name = name, size = size, compressedSize = size, crc32 = crc, method = ZipEntry.STORED, buffer = input)
      input.writerIndex(size + headerSize)
      assert(input.readableBytes() == (size + headerSize))
      resultStream.writeRawEntry(
        data = input,
        name = name,
        size = size,
        compressedSize = size,
        method = ZipEntry.STORED,
        crc = crc,
        headerSize = headerSize,
      )
    }
    finally {
      input?.release()
    }
  }

  private fun writeLargeFile(fileSize: Long, channel: FileChannel, deflater: Deflater?): Long {
    // channel.transferTo will use a slow path for untrusted (custom) WritableByteChannel implementations, so, duplicate what JDK does
    // see FileChannelImpl.transferFromFileChannel
    var remaining = fileSize
    var position = 0L
    var compressedSize = 0L

    var effectiveDeflater = deflater

    while (remaining > 0L) {
      val size = min(remaining, mappedTransferSize)
      val buffer = channel.map(MapMode.READ_ONLY, position, size)

      remaining -= size
      position += size

      try {
        buffer.mark()
        crc32.update(buffer)
        buffer.reset()
        buffer.mark()

        if (effectiveDeflater == null) {
          resultStream.writeBuffer(buffer)
          compressedSize = -1
        }
        else {
          val output = deflateBufferAllocator!!.allocate(size.toInt() + 4096)
          effectiveDeflater.setInput(buffer)

          if (remaining <= 0) {
            effectiveDeflater.finish()
          }

          do {
            val n = effectiveDeflater.deflate(output, Deflater.SYNC_FLUSH)
            assert(n != 0)
          }
          while (buffer.hasRemaining())

          output.flip()
          compressedSize += output.remaining()

          if (position == 0L && compressedSize > size) {
            // incompressible
            effectiveDeflater = null
            buffer.reset()
            resultStream.writeBuffer(buffer)
            compressedSize = -1
          }
          else {
            resultStream.writeBuffer(output)
          }
        }
      }
      finally {
        unmapBuffer(buffer)
      }
    }

    effectiveDeflater?.reset()
    return compressedSize
  }

  fun compressedData(nameString: String, data: ByteBuffer) {
    val name = nameString.toByteArray()
    val headerSize = 30 + name.size

    val size = data.remaining()

    data.mark()
    crc32.reset()
    crc32.update(data)
    val crc = crc32.value
    data.reset()

    val output = deflateBufferAllocator!!.allocate(headerSize + size + 4096)
    output.position(headerSize)

    deflater!!.setInput(data)
    deflater.finish()
    do {
      val n = deflater.deflate(output, Deflater.SYNC_FLUSH)
      assert(n != 0)
    }
    while (data.hasRemaining())
    deflater.reset()

    output.limit(output.position())
    output.position(0)
    val compressedSize = output.remaining() - headerSize
    val nettyBuffer = Unpooled.wrappedBuffer(output)
    nettyBuffer.clear()
    writeZipLocalFileHeader(
      name = name,
      size = size,
      compressedSize = compressedSize,
      crc32 = crc,
      method = ZipEntry.DEFLATED,
      buffer = nettyBuffer,
    )
    nettyBuffer.setIndex(0, compressedSize + headerSize)
    assert(nettyBuffer.readableBytes() == (compressedSize + headerSize))
    resultStream.writeRawEntry(
      data = nettyBuffer,
      name = name,
      size = size,
      compressedSize = compressedSize,
      method = ZipEntry.DEFLATED,
      crc = crc,
      headerSize = headerSize,
    )
  }

  fun uncompressedData(nameString: String, data: String) {
    uncompressedData(nameString = nameString, data = data.toByteArray())
  }

  fun uncompressedData(nameString: String, data: ByteArray) {
    val name = nameString.toByteArray()

    val size = data.size
    if (size == 0) {
      resultStream.writeEmptyFile(name)
      return
    }

    crc32.reset()
    crc32.update(data)
    val crc = crc32.value

    resultStream.writeDataRawEntry(name = name, data = data, size = size, crc = crc)
  }

  fun uncompressedData(nameString: String, data: ByteBuffer) {
    val name = nameString.toByteArray()

    val size = data.remaining()
    if (size == 0) {
      resultStream.writeEmptyFile(name = name)
      return
    }

    data.mark()
    crc32.reset()
    crc32.update(data)
    val crc = crc32.value
    data.reset()

    resultStream.writeDataRawEntry(data = data, name = name, size = size, compressedSize = size, method = ZipEntry.STORED, crc = crc)
  }

  fun uncompressedData(nameString: String, maxSize: Int, dataWriter: (ByteBuf) -> Unit) {
    val name = nameString.toByteArray()
    val headerSize = 30 + name.size

    val bufCapacity = headerSize + maxSize
    ByteBufAllocator.DEFAULT.ioBuffer(bufCapacity, bufCapacity).use { data ->
      data.writerIndex(headerSize)
      dataWriter(data)
      val size = data.readableBytes() - headerSize

      crc32.reset()
      crc32.update(data.nioBuffer(headerSize, size))
      val crc = crc32.value

      data.writerIndex(0)
      writeZipLocalFileHeader(name = name, size = size, compressedSize = size, crc32 = crc, method = ZipEntry.STORED, buffer = data)
      data.writerIndex(headerSize + size)
      assert(data.readableBytes() == (size + headerSize))
      resultStream.writeRawEntry(
        data = data,
        name = name,
        size = size,
        compressedSize = size,
        method = ZipEntry.STORED,
        crc = crc,
        headerSize = headerSize,
      )
    }
  }

  fun dir(name: String) {
    resultStream.addDirEntry(name)
  }

  override fun close() {
    @Suppress("ConvertTryFinallyToUseCall")
    try {
      deflateBufferAllocator?.close()
      deflater?.end()
    }
    finally {
      resultStream.close()
    }
  }
}