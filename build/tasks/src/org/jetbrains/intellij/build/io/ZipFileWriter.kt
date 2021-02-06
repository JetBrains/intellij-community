// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry

// not thread-safe, intended only for single thread for one time use
private class ByteBufferAllocator {
  private var directByteBuffer: ByteBuffer? = null

  fun allocate(size: Int): ByteBuffer {
    var result = directByteBuffer
    if (result != null && result.capacity() < size) {
      // clear references to object to make sure that it can be collected by GC
      directByteBuffer = null
      result = null
    }

    if (result == null) {
      result = ByteBuffer.allocateDirect(roundUpInt(size, 65_536))!!
      result.order(ByteOrder.LITTLE_ENDIAN)
      directByteBuffer = result
    }
    result.rewind()
    result.limit(size)
    return result
  }
}

internal class ZipFileWriter(channel: FileChannel, private val deflater: Deflater?) : Closeable {
  private val resultStream = ZipArchiveOutputStream(channel)
  private val crc32 = CRC32()

  private val bufferAllocator = ByteBufferAllocator()
  private val inflateBufferAllocator = if (deflater == null) null else ByteBufferAllocator()

  fun writeEntry(nameString: String, method: Int, file: Path) {
    val name = nameString.toByteArray()
    val headerSize = 30 + name.size
    var isCompressed = method == ZipEntry.DEFLATED

    val input: ByteBuffer
    val size: Int
    Files.newByteChannel(file, EnumSet.of(StandardOpenOption.READ)).use { channel ->
      size = channel.size().toInt()
      if (size < 512) {
        isCompressed = false
      }

      when {
        size == 0 -> {
          writeEmptyFile(name, headerSize)
          return
        }
        isCompressed -> {
          try {
            input = bufferAllocator.allocate(size)
          }
          catch (e: OutOfMemoryError) {
            throw RuntimeException("Cannot allocate write buffer for $nameString (size=$size)", e)
          }
        }
        else -> {
          input = bufferAllocator.allocate(headerSize + size)
          input.position(headerSize)
        }
      }

      // set position to compute CRC
      input.mark()
      do {
        channel.read(input)
      }
      while (input.hasRemaining())
      input.reset()
    }

    crc32.reset()
    crc32.update(input)
    val crc = crc32.value
    input.position(0)

    if (isCompressed) {
      val output = inflateBufferAllocator!!.allocate(headerSize + size + 4096)
      output.position(headerSize)

      deflater!!.setInput(input)
      deflater.finish()
      do {
        val n = deflater.deflate(output, Deflater.NO_FLUSH)
        assert(n != 0)
      }
      while (input.hasRemaining())
      deflater.reset()

      writeCompressedData(name, size, crc, headerSize, output)
    }
    else {
      writeLocalFileHeader(name, size, size, crc, ZipEntry.STORED, input)
      input.position(0)
      assert(input.remaining() == (size + headerSize))
      resultStream.writeRawEntry(input, name, size, size, ZipEntry.STORED, crc)
    }
  }

  fun writeEntry(nameString: String, method: Int, size: Int, stream: InputStream) {
    assert(size >= 0)

    val name = nameString.toByteArray()
    val headerSize = 30 + name.size

    if (size == 0) {
      writeEmptyFile(name, headerSize)
      return
    }

    val input = stream.use { it.readNBytes(size) }

    crc32.reset()
    crc32.update(input)
    val crc = crc32.value

    if (method == ZipEntry.DEFLATED && size >= 512) {
      val output = bufferAllocator.allocate(headerSize + size + (size / 2))
      output.position(headerSize)

      deflater!!.setInput(input)
      deflater.finish()
      do {
        val n = deflater.deflate(output, Deflater.NO_FLUSH)
        assert(n != 0)
      }
      while (!deflater.finished())
      deflater.reset()

      writeCompressedData(name, size, crc, headerSize, output)
    }
    else {
      val output = bufferAllocator.allocate(headerSize + size)
      writeLocalFileHeader(name, size, size, crc, ZipEntry.STORED, output)
      output.put(input)
      output.flip()
      assert(output.remaining() == (size + headerSize))
      resultStream.writeRawEntry(output, name, size, size, ZipEntry.STORED, crc)
    }
  }

  fun writeUncompressedEntry(nameString: String, data: ByteBuffer) {
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

  fun writeUncompressedEntry(nameString: String, maxSize: Int, dataWriter: (ByteBuffer) -> Unit) {
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
    resultStream.writeRawEntry(output, name, size, size, ZipEntry.STORED, crc)
  }

  private fun writeCompressedData(name: ByteArray, size: Int, crc: Long, headerSize: Int, output: ByteBuffer) {
    output.limit(output.position())
    output.position(0)
    val compressedSize = output.remaining() - headerSize
    writeLocalFileHeader(name, size, compressedSize, crc, ZipEntry.DEFLATED, output)
    output.position(0)
    assert(output.remaining() == (compressedSize + headerSize))
    resultStream.writeRawEntry(output, name, size, compressedSize, ZipEntry.DEFLATED, crc)
  }

  private fun writeEmptyFile(name: ByteArray, headerSize: Int) {
    val input = bufferAllocator.allocate(headerSize)
    writeLocalFileHeader(name, size = 0, compressedSize = 0, crc32 = 0, method = ZipEntry.STORED, buffer = input)
    input.position(0)
    input.limit(headerSize)
    resultStream.writeRawEntry(input, name, 0, 0, ZipEntry.STORED, 0)
  }

  fun addDirEntry(name: String) {
    assert(name.endsWith('/'))
    resultStream.addDirEntry(name.toByteArray())
  }

  fun finish(comment: ByteBuffer? = null) {
    resultStream.finish(comment)
  }

  override fun close() {
    resultStream.close()
  }
}

private fun writeLocalFileHeader(name: ByteArray, size: Int, compressedSize: Int, crc32: Long, method: Int, buffer: ByteBuffer): Int {
  buffer.putInt(0x04034b50)
  // Version needed to extract (minimum)
  buffer.putShort(0)
  // General purpose bit flag
  buffer.putShort(0)
  // Compression method
  buffer.putShort(method.toShort())
  // File last modification time
  buffer.putShort(0)
  // File last modification date
  buffer.putShort(0)
  // CRC-32 of uncompressed data
  buffer.putInt((crc32 and 0xffffffffL).toInt())
  val compressedSizeOffset = buffer.position()
  // Compressed size
  buffer.putInt(compressedSize)
  // Uncompressed size
  buffer.putInt(size)
  // File name length
  buffer.putShort((name.size and 0xffff).toShort())
  // Extra field length
  buffer.putShort(0)
  buffer.put(name)
  return compressedSizeOffset
}

private fun roundUpInt(x: Int, @Suppress("SameParameterValue") blockSizePowerOf2: Int): Int {
  return x + blockSizePowerOf2 - 1 and -blockSizePowerOf2
}