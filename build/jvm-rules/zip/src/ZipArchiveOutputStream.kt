// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.GatheringByteChannel
import java.nio.channels.SeekableByteChannel
import java.util.zip.CRC32
import java.util.zip.ZipEntry

const val INDEX_FILENAME: String = "__index__"
private val INDEX_FILENAME_BYTES = "__index__".toByteArray()

// 1MB
private const val FLUSH_THRESHOLD = 1 * 1024 * 1024

class ZipArchiveOutputStream(
  private val channel: GatheringByteChannel,
  private val zipIndexWriter: ZipIndexWriter,
) : AutoCloseable {
  private var finished = false

  private var bufferReleased = false
  @PublishedApi
  internal val buffer: ByteBuf = ByteBufAllocator.DEFAULT.directBuffer(64 * 1024)

  private var channelPosition = 0L

  private val fileChannel = channel as? FileChannel

  internal fun addDirEntry(name: String) {
    val localFileHeaderOffset = flushBufferIfNeeded()

    assert(!name.endsWith('/'))
    val key = name.toByteArray()
    val nameInArchive = key + '/'.code.toByte()

    buffer.writeIntLE(0x04034b50)
    // Version needed to extract (minimum)
    buffer.writeShortLE(0)
    // General purpose bit flag
    buffer.writeShortLE(0)
    // Compression method
    buffer.writeShortLE(ZipEntry.STORED)
    // File last modification time
    buffer.writeShortLE(0)
    // File last modification date
    buffer.writeShortLE(0)
    // CRC-32 of uncompressed data
    buffer.writeIntLE(0)
    // Compressed size
    buffer.writeIntLE(0)
    // Uncompressed size
    buffer.writeIntLE(0)
    // File name length
    buffer.writeShortLE(nameInArchive.size and 0xffff)
    // Extra field length
    buffer.writeShortLE(0)
    buffer.writeBytes(nameInArchive)

    zipIndexWriter.writeCentralFileHeader(
      size = 0,
      compressedSize = 0,
      method = ZipEntry.STORED,
      crc = 0,
      name = nameInArchive,
      localFileHeaderOffset = localFileHeaderOffset,
      dataOffset = -1,
      normalName = key,
    )
  }

  fun writeWithCrc(path: ByteArray, estimatedSize: Int, crc32: CRC32?, task: (ByteBuf) -> Unit) {
    val localFileHeaderOffset = flushBufferIfNeeded()
    val headerSize = 30 + path.size
    val dataOffset = localFileHeaderOffset + headerSize

    buffer.ensureWritable(headerSize + estimatedSize.coerceAtLeast(1024))

    val headerPosition = buffer.writerIndex()
    val endOfHeaderPosition = headerPosition + headerSize
    buffer.writerIndex(endOfHeaderPosition)

    task(buffer)

    val size = buffer.writerIndex() - endOfHeaderPosition

    val crc = if (crc32 == null || size == 0) {
      0
    }
    else {
      crc32.reset()
      val nioBuffer = buffer.nioBuffer(endOfHeaderPosition, size)
      crc32.update(nioBuffer)
      crc32.value
    }

    buffer.writerIndex(headerPosition)

    writeZipLocalFileHeader(
      name = path,
      size = size,
      compressedSize = size,
      crc32 = crc,
      method = ZipEntry.STORED,
      buffer = buffer,
    )
    assert(buffer.writerIndex() == endOfHeaderPosition)
    buffer.writerIndex(endOfHeaderPosition + size)

    zipIndexWriter.writeCentralFileHeader(
      size = size,
      compressedSize = size,
      method = ZipEntry.STORED,
      crc = crc,
      name = path,
      localFileHeaderOffset = localFileHeaderOffset,
      dataOffset = dataOffset,
    )
  }

  fun write(path: ByteArray, estimatedSize: Int, task: (ByteBuf) -> Unit) {
    writeWithCrc(path, estimatedSize, crc32 = null, task = task)
  }

  // returns start position
  inline fun writeUndeclaredData(task: (ByteBuf, Long) -> Unit): Long {
    val position = flushBufferIfNeeded()
    task(buffer, position)
    return position
  }

  @PublishedApi
  internal fun flushBufferIfNeeded(threshold: Int = FLUSH_THRESHOLD): Long {
    if (bufferReleased) {
      throw IOException("Stream has already been finished")
    }

    val readableBytes = buffer.readableBytes()
    if (readableBytes > threshold) {
      writeBuffer(buffer)
      buffer.clear()
      return channelPosition
    }
    else {
      return channelPosition + readableBytes
    }
  }

  // data contains data and zip local file header
  fun writeRawEntry(
    data: ByteBuf,
    name: ByteArray,
    size: Int,
    compressedSize: Int,
    method: Int,
    crc: Long,
    headerSize: Int,
  ) {
    val localFileHeaderOffset = flushBufferIfNeeded()
    buffer.writeBytes(data)

    zipIndexWriter.writeCentralFileHeader(
      size = size,
      compressedSize = compressedSize,
      method = method,
      crc = crc,
      name = name,
      localFileHeaderOffset = localFileHeaderOffset,
      dataOffset = localFileHeaderOffset + headerSize,
    )
  }

  fun writeEntryHeaderWithoutCrc(name: ByteArray, size: Int) {
    // always flush - this method doesn't yet support pending buffer
    val localFileHeaderOffset = flushBufferIfNeeded(threshold = 0)
    val headerSize = 30 + name.size
    val dataOffset = channelPosition + headerSize
    writeZipLocalFileHeader(name = name, size = size, compressedSize = size, crc32 = 0, method = ZipEntry.STORED, buffer = buffer)
    assert(buffer.readableBytes() == headerSize)
    writeBuffer(buffer)
    zipIndexWriter.writeCentralFileHeader(
      size = size,
      compressedSize = size,
      method = ZipEntry.STORED,
      crc = 0,
      name = name,
      localFileHeaderOffset = localFileHeaderOffset,
      dataOffset = dataOffset,
    )
    buffer.clear()
  }

  internal fun writeEntryHeaderAt(
    name: ByteArray,
    position: Long,
    size: Int,
    compressedSize: Int,
    crc: Long,
    method: Int,
  ) {
    // always flush - this method doesn't yet support pending buffer
    flushBufferIfNeeded(threshold = 0)

    val headerSize = 30 + name.size
    val dataOffset = position + headerSize

    writeZipLocalFileHeader(name = name, size = size, compressedSize = compressedSize, crc32 = crc, method = method, buffer = buffer)
    assert(buffer.readableBytes() == headerSize)

    if (fileChannel == null) {
      val c = channel as SeekableByteChannel
      c.position(position)
      writeToChannelFully(channel, buffer)
      c.position(channelPosition)
    }
    else {
      writeToFileChannelFully(channel = fileChannel, position = position, buffer = buffer)
    }

    assert(channelPosition == dataOffset + compressedSize)
    zipIndexWriter.writeCentralFileHeader(
      size = size,
      compressedSize = compressedSize,
      method = method,
      crc = crc,
      name = name,
      localFileHeaderOffset = position,
      dataOffset = dataOffset,
    )
  }

  private fun writeIndex(indexWriter: IkvIndexBuilder, buffer: ByteBuf, indexDataSize: Int) {
    // write package class and resource hashes
    val classPackages = indexWriter.classPackages
    val resourcePackages = indexWriter.resourcePackages
    val size = (classPackages.size + resourcePackages.size) * Long.SIZE_BYTES
    val lengthHeaderSize = Int.SIZE_BYTES * 2

    buffer.ensureWritable(indexDataSize + lengthHeaderSize + size)
    indexWriter.write(buffer)
    if (size == 0) {
      buffer.writeZero(lengthHeaderSize)
    }
    else {
      val classPackageArray = indexWriter.classPackages.toLongArray()
      val resourcePackageArray = indexWriter.resourcePackages.toLongArray()

      // same content for same data
      classPackageArray.sort()
      resourcePackageArray.sort()

      buffer.writeIntLE(classPackages.size)
      buffer.writeIntLE(resourcePackages.size)
      val longBuffer = buffer.nioBuffer(buffer.writerIndex(), size).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
      longBuffer.put(classPackageArray)
      longBuffer.put(resourcePackageArray)
      buffer.writerIndex(buffer.writerIndex() + size)
    }

    // write names
    buffer.ensureWritable(indexWriter.names.sumOf { it.size + Short.SIZE_BYTES })
    for (name in indexWriter.names) {
      buffer.writeShortLE(name.size)
    }

    for (name in indexWriter.names) {
      buffer.writeBytes(name)
    }
  }

  internal fun finish(indexWriter: IkvIndexBuilder?) {
    flushBufferIfNeeded()

    val indexDataEnd = if (indexWriter == null || zipIndexWriter.entryCount == 0) {
      -1
    }
    else {
      // ditto on macOS doesn't like arbitrary data in zip file - wrap into zip entry
      val indexDataSize = indexWriter.dataSize()
      val indexDataEnd = flushBufferIfNeeded() + indexDataSize + 30 + INDEX_FILENAME_BYTES.size
      writeWithCrc(INDEX_FILENAME_BYTES, estimatedSize = indexDataSize, crc32 = if (indexWriter.writeCrc32) CRC32() else null) { buffer ->
        writeIndex(indexWriter, buffer, indexDataSize)
      }
      indexDataEnd.toInt()
    }

    flushBufferIfNeeded(threshold = 0)

    buffer.release()
    bufferReleased = true

    // write central directory file header
    val zipIndexData = zipIndexWriter.finish(centralDirectoryOffset = channelPosition, indexWriter = indexWriter, indexDataEnd = indexDataEnd)
    writeBuffer(zipIndexData)
    zipIndexWriter.release()

    finished = true
  }

  internal fun getChannelPosition(): Long = flushBufferIfNeeded(threshold = 0)

  internal fun getChannelPositionAndAdd(increment: Int): Long {
    val p = flushBufferIfNeeded(threshold = 0)
    channelPosition += increment.toLong()
    if (fileChannel == null) {
      (channel as SeekableByteChannel).position(channelPosition)
    }
    return p
  }

  internal fun transferFrom(source: FileChannel, size: Long) {
    require(!buffer.isReadable)

    var position = 0L
    val to = this.fileChannel!!
    while (position < size) {
      val n = to.transferFrom(source, channelPosition, size - position)
      assert(n >= 0)
      position += n
      channelPosition += n
    }
  }

  private fun writeBuffer(buffer: ByteBuf) {
    val size = if (fileChannel == null) {
      writeToChannelFully(channel = channel, buffer = buffer)
    }
    else {
      writeToFileChannelFully(channel = fileChannel, position = channelPosition, buffer = buffer)
    }

    channelPosition += size
  }

  internal fun writeBuffer(data: ByteBuffer) {
    if (fileChannel == null) {
      val size = data.remaining()
      do {
        channel.write(data)
      }
      while (data.hasRemaining())
      channelPosition += size
    }
    else {
      var currentPosition = channelPosition
      do {
        currentPosition += fileChannel.write(data, currentPosition)
      }
      while (data.hasRemaining())
      channelPosition = currentPosition
    }
  }

  override fun close() {
    try {
      if (!finished) {
        channel.use {
          finish(indexWriter = null)
        }
      }
    }
    finally {
      zipIndexWriter.release()
      if (!bufferReleased) {
        buffer.release()
        bufferReleased = true
      }
    }
  }
}

fun writeZipLocalFileHeader(name: ByteArray, size: Int, compressedSize: Int, crc32: Long, method: Int, buffer: ByteBuf) {
  buffer.writeIntLE(0x04034b50)
  // Version needed to extract (2), General purpose bit flag (2)
  buffer.writeZero(4)
  // Compression method
  buffer.writeShortLE(method)
  // File last modification time (2), File last modification date (2)
  buffer.writeZero(4)
  // CRC-32 of uncompressed data
  buffer.writeIntLE((crc32 and 0xffffffffL).toInt())
  // Compressed size
  buffer.writeIntLE(compressedSize)
  // Uncompressed size
  buffer.writeIntLE(size)
  // File name length
  buffer.writeShortLE(name.size and 0xffff)
  // Extra field length (2)
  buffer.writeZero(2)
  buffer.writeBytes(name)
}
