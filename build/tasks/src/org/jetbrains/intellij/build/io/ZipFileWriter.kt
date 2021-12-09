// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import kotlin.math.min

// 1 MB
private const val largeFileThreshold = 1_048_576
private const val compressThreshold = 8 * 1024
// 8 MB (as JDK)
private const val mappedTransferSize = 8L * 1024L * 1024L

internal inline fun writeNewZip(file: Path, compress: Boolean = false, task: (ZipFileWriter) -> Unit) {
  Files.createDirectories(file.parent)
  ZipFileWriter(channel = FileChannel.open(file, W_CREATE_NEW),
                deflater = if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null).use {
    task(it)
  }
}

// you must pass SeekableByteChannel if files will be written (`file` method)
internal class ZipFileWriter(channel: WritableByteChannel, private val deflater: Deflater? = null) : AutoCloseable {
  constructor(channel: WritableByteChannel, compress: Boolean)
    : this(channel = channel, deflater = if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null)

  // size is written as part of optimized metadata - so, if compression is enabled, optimized metadata will be incorrect
  val resultStream = ZipArchiveOutputStream(channel, withOptimizedMetadataEnabled = deflater == null)
  private val crc32 = CRC32()

  private val bufferAllocator = ByteBufferAllocator()
  private val deflateBufferAllocator = if (deflater == null) null else ByteBufferAllocator()

  @Suppress("DuplicatedCode")
  fun file(nameString: String, file: Path) {
    var isCompressed = deflater != null && !nameString.endsWith(".png")

    val name = nameString.toByteArray()
    val headerSize = 30 + name.size

    crc32.reset()

    val input: ByteBuffer
    val size: Int
    FileChannel.open(file, EnumSet.of(StandardOpenOption.READ)).use { channel ->
      size = channel.size().toInt()
      if (size == 0) {
        writeEmptyFile(name, headerSize)
        return
      }
      if (size < compressThreshold) {
        isCompressed = false
      }

      if (size > largeFileThreshold || isCompressed) {
        val headerPosition = resultStream.getChannelPositionAndAdd(headerSize)
        var compressedSize = writeLargeFile(size.toLong(), channel, if (isCompressed) deflater else null).toInt()
        val crc = crc32.value
        val method: Int
        if (compressedSize == -1) {
          method = ZipEntry.STORED
          compressedSize = size
        }
        else {
          method = ZipEntry.DEFLATED
        }
        val buffer = bufferAllocator.allocate(headerSize)
        writeLocalFileHeader(name = name, size = size, compressedSize = compressedSize, crc32 = crc, method = method, buffer = buffer)
        buffer.position(0)
        assert(buffer.remaining() == headerSize)
        resultStream.writeEntryHeaderAt(name = name,
                                        header = buffer,
                                        position = headerPosition,
                                        size = size,
                                        compressedSize = compressedSize,
                                        crc = crc,
                                        method = method)
        return
      }

      input = bufferAllocator.allocate(headerSize + size)
      input.position(headerSize)

      // set position to compute CRC
      input.mark()
      do {
        channel.read(input)
      }
      while (input.hasRemaining())
      input.reset()
      crc32.update(input)
    }

    val crc = crc32.value
    input.position(0)

    writeLocalFileHeader(name, size, size, crc, ZipEntry.STORED, input)
    input.position(0)
    assert(input.remaining() == (size + headerSize))
    resultStream.writeRawEntry(input, name, size, size, ZipEntry.STORED, crc, headerSize)
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

  @Suppress("DuplicatedCode")
  fun compressedData(nameString: String, data: ByteArray) {
    val name = nameString.toByteArray()
    val headerSize = 30 + name.size

    val input = ByteBuffer.wrap(data)
    val size = data.size

    crc32.reset()
    crc32.update(data)
    val crc = crc32.value
    input.position(0)

    val output = deflateBufferAllocator!!.allocate(headerSize + size + 4096)
    output.position(headerSize)

    deflater!!.setInput(input)
    deflater.finish()
    do {
      val n = deflater.deflate(output, Deflater.SYNC_FLUSH)
      assert(n != 0)
    }
    while (input.hasRemaining())
    deflater.reset()

    output.limit(output.position())
    output.position(0)
    val compressedSize = output.remaining() - headerSize
    writeLocalFileHeader(name, size, compressedSize, crc, ZipEntry.DEFLATED, output)
    output.position(0)
    assert(output.remaining() == (compressedSize + headerSize))
    resultStream.writeRawEntry(output, name, size, compressedSize, ZipEntry.DEFLATED, crc, headerSize)
  }

  fun uncompressedData(nameString: String, data: String) {
    uncompressedData(nameString, ByteBuffer.wrap(data.toByteArray()))
  }

  fun uncompressedData(nameString: String, data: ByteBuffer) {
    val name = nameString.toByteArray()
    val headerSize = 30 + name.size

    if (!data.hasRemaining()) {
      writeEmptyFile(name, headerSize)
      return
    }

    data.mark()
    crc32.reset()
    crc32.update(data)
    val crc = crc32.value
    data.reset()

    val size = data.remaining()
    val header = bufferAllocator.allocate(headerSize)
    writeLocalFileHeader(name, size, size, crc, ZipEntry.STORED, header)
    header.position(0)
    resultStream.writeRawEntry(header, data, name, size, size, ZipEntry.STORED, crc)
  }

  fun uncompressedData(nameString: String, maxSize: Int, dataWriter: (ByteBuffer) -> Unit) {
    val name = nameString.toByteArray()
    val headerSize = 30 + name.size

    val output = bufferAllocator.allocate(headerSize + maxSize)
    output.position(headerSize)
    dataWriter(output)
    output.limit(output.position())
    output.position(headerSize)
    val size = output.remaining()

    crc32.reset()
    crc32.update(output)
    val crc = crc32.value
    output.position(0)

    writeLocalFileHeader(name, size, size, crc, ZipEntry.STORED, output)
    output.position(0)
    assert(output.remaining() == (size + headerSize))
    resultStream.writeRawEntry(output, name, size, size, ZipEntry.STORED, crc, headerSize)
  }

  private fun writeEmptyFile(name: ByteArray, headerSize: Int) {
    val input = bufferAllocator.allocate(headerSize)
    writeLocalFileHeader(name, size = 0, compressedSize = 0, crc32 = 0, method = ZipEntry.STORED, buffer = input)
    input.position(0)
    input.limit(headerSize)
    resultStream.writeRawEntry(input, name, 0, 0, ZipEntry.STORED, 0, headerSize)
  }

  fun dir(name: String) {
    resultStream.addDirEntry(name)
  }

  override fun close() {
    @Suppress("ConvertTryFinallyToUseCall")
    try {
      bufferAllocator.close()
      deflateBufferAllocator?.close()
      deflater?.end()
    }
    finally {
      resultStream.close()
    }
  }
}