// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

class ZipArchiveOutputStream(
  private val channel: GatheringByteChannel,
  private val zipIndexWriter: ZipIndexWriter,
) : AutoCloseable {
  private var finished = false

  private var bufferReleased = false
  private val buffer = ByteBufAllocator.DEFAULT.directBuffer(512 * 1024)

  private var channelPosition = 0L

  private val fileChannel = channel as? FileChannel

  fun addDirEntry(name: String) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val localFileHeaderOffset = channelPosition

    assert(!name.endsWith('/'))
    val key = name.toByteArray()
    val nameInArchive = key + '/'.code.toByte()

    buffer.clear()
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

    writeBuffer()

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

  fun writeDataRawEntryWithoutCrc(data: ByteBuffer, name: ByteArray) {
    val size = data.remaining()
    writeDataRawEntry(data = data, name = name, size = size, compressedSize = size, method = ZipEntry.STORED, crc = 0)
  }

  // data contains only data - zip local file header will be generated
  fun writeDataRawEntry(
    data: ByteBuffer,
    name: ByteArray,
    size: Int,
    compressedSize: Int,
    method: Int,
    crc: Long,
  ) {
    assert(method != -1)

    if (finished) {
      throw IOException("Stream has already been finished")
    }

    buffer.clear()
    writeZipLocalFileHeader(name = name, size = size, compressedSize = compressedSize, crc32 = crc, method = method, buffer = buffer)

    val localFileHeaderOffset = channelPosition
    val dataOffset = localFileHeaderOffset + buffer.readableBytes()

    writeBuffer()
    writeBuffer(data)

    zipIndexWriter.writeCentralFileHeader(
      size = size,
      compressedSize = compressedSize,
      method = method,
      crc = crc,
      name = name,
      localFileHeaderOffset = localFileHeaderOffset,
      dataOffset = dataOffset,
    )
  }

  fun writeEmptyFile(name: ByteArray) {
    buffer.clear()
    writeZipLocalFileHeader(name = name, size = 0, compressedSize = 0, crc32 = 0, method = ZipEntry.STORED, buffer = buffer)
    writeRawEntry(
      data = buffer,
      name = name,
      size = 0,
      compressedSize = 0,
      method = ZipEntry.STORED,
      crc = 0,
      headerSize = 30 + name.size,
    )
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
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val localFileHeaderOffset = channelPosition
    assert(method != -1)

    if (fileChannel == null) {
      writeToChannelFully(channel, data)
    }
    else {
      writeToFileChannelFully(fileChannel, channelPosition, data)
    }
    channelPosition += compressedSize + headerSize

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
    val localFileHeaderOffset = channelPosition
    val headerSize = 30 + name.size
    val dataOffset = channelPosition + headerSize

    buffer.clear()
    writeZipLocalFileHeader(name = name, size = size, compressedSize = size, crc32 = 0, method = ZipEntry.STORED, buffer = buffer)
    assert(buffer.readableBytes() == headerSize)
    writeBuffer()

    zipIndexWriter.writeCentralFileHeader(
      size = size,
      compressedSize = size,
      method = ZipEntry.STORED,
      crc = 0,
      name = name,
      localFileHeaderOffset = localFileHeaderOffset,
      dataOffset = dataOffset,
    )
  }

  fun writeEntryHeaderAt(
    name: ByteArray,
    position: Long,
    size: Int,
    compressedSize: Int,
    crc: Long,
    method: Int,
  ) {
    val headerSize = 30 + name.size
    val dataOffset = position + headerSize

    buffer.clear()
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

  private fun writeIndex(crc32: CRC32, indexWriter: IkvIndexBuilder): Int {
    fun writeData(task: (ByteBuf) -> Unit) {
      buffer.clear()
      task(buffer)

      crc32.update(buffer.nioBuffer())

      writeBuffer()
    }

    // write one by one to channel to avoid buffer overflow
    writeData {
      indexWriter.write(it)
    }

    val indexDataEnd = channelPosition.toInt()

    // write package class and resource hashes
    writeData { buffer ->
      val classPackages = indexWriter.classPackages
      val resourcePackages = indexWriter.resourcePackages
      val size = (classPackages.size + resourcePackages.size) * Long.SIZE_BYTES
      val lengthHeaderSize = Int.SIZE_BYTES * 2
      if (size == 0) {
        buffer.writeZero(lengthHeaderSize)
      }
      else {
        buffer.ensureWritable(lengthHeaderSize + size)

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
    }

    // write names
    for (list in indexWriter.names.asSequence().chunked(4096)) {
      writeData { buffer ->
        for (name in list) {
          buffer.writeShortLE(name.size)
        }
      }
    }

    for (list in indexWriter.names.asSequence().chunked(1024)) {
      writeData { buffer ->
        for (name in list) {
          buffer.writeBytes(name)
        }
      }
    }

    return indexDataEnd
  }

  internal fun finish(indexWriter: IkvIndexBuilder?) {
    if (finished) {
      throw IOException("This archive has already been finished")
    }

    val indexOffset = if (indexWriter == null || zipIndexWriter.entryCount == 0) {
      -1
    }
    else {
      writeIndexFile(indexWriter, INDEX_FILENAME_BYTES)
    }

    buffer.release()
    bufferReleased = true

    // write central directory file header
    zipIndexWriter.finish(
      centralDirectoryOffset = channelPosition,
      indexWriter = indexWriter,
      indexOffset = indexOffset,
    )
    writeBuffer(zipIndexWriter.buffer)
    zipIndexWriter.release()

    finished = true
  }

  private fun writeIndexFile(indexWriter: IkvIndexBuilder, @Suppress("SameParameterValue") name: ByteArray): Int {
    // ditto on macOS doesn't like arbitrary data in zip file - wrap into zip entry
    val headerSize = 30 + name.size
    val headerPosition = getChannelPositionAndAdd(headerSize)
    val entryDataPosition = channelPosition

    val crc32 = CRC32()
    val indexOffset = writeIndex(crc32, indexWriter)

    val size = (channelPosition - entryDataPosition).toInt()
    writeEntryHeaderAt(
      name = name,
      position = headerPosition,
      size = size,
      compressedSize = size,
      crc = crc32.value,
      method = ZipEntry.STORED,
    )
    return indexOffset
  }

  internal fun getChannelPosition(): Long = channelPosition

  internal fun getChannelPositionAndAdd(increment: Int): Long {
    val p = channelPosition
    channelPosition += increment.toLong()
    if (fileChannel == null) {
      (channel as SeekableByteChannel).position(channelPosition)
    }
    return p
  }

  internal fun transferFrom(source: FileChannel, size: Long) {
    var position = 0L
    val to = this.fileChannel!!
    while (position < size) {
      val n = to.transferFrom(source, channelPosition, size - position)
      assert(n >= 0)
      position += n
      channelPosition += n
    }
  }

  private fun writeBuffer(buffer: ByteBuf = this.buffer): Int {
    val size = if (fileChannel == null) {
      writeToChannelFully(channel = channel, buffer = buffer)
    }
    else {
      writeToFileChannelFully(channel = fileChannel, position = channelPosition, buffer = buffer)
    }

    channelPosition += size
    buffer.clear()
    return size
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

fun writeZipLocalFileHeader(name: ByteArray, size: Int, compressedSize: Int, crc32: Long, method: Int, buffer: ByteBuf): Int {
  buffer.writeIntLE(0x04034b50)
  // Version needed to extract (2), General purpose bit flag (2)
  buffer.writeZero(4)
  // Compression method
  buffer.writeShortLE(method)
  // File last modification time (2), File last modification date (2)
  buffer.writeZero(4)
  // CRC-32 of uncompressed data
  buffer.writeIntLE((crc32 and 0xffffffffL).toInt())
  val compressedSizeOffset = buffer.writerIndex()
  // Compressed size
  buffer.writeIntLE(compressedSize)
  // Uncompressed size
  buffer.writeIntLE(size)
  // File name length
  buffer.writeShortLE(name.size and 0xffff)
  // Extra field length (2)
  buffer.writeZero(2)
  buffer.writeBytes(name)
  return compressedSizeOffset
}
