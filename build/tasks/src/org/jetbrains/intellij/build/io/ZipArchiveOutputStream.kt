// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.Xxh3
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.zip.CRC32
import java.util.zip.ZipEntry

private const val INDEX_FORMAT_VERSION: Byte = 4

const val INDEX_FILENAME: String = "__index__"

internal class ZipArchiveOutputStream(
  private val channel: WritableByteChannel,
) : AutoCloseable {
  private var finished = false
  private var entryCount = 0

  private var metadataBuffer = ByteBuffer.allocateDirect(2 * 1024 * 1024).order(ByteOrder.LITTLE_ENDIAN)

  // 1 MB should be enough for the end of the central directory record
  private val buffer = ByteBuffer.allocateDirect(1024 * 1024).order(ByteOrder.LITTLE_ENDIAN)

  private var channelPosition = 0L

  private val fileChannel = channel as? FileChannel

  fun addDirEntry(name: String, indexWriter: IkvIndexBuilder?) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channelPosition
    entryCount++

    assert(!name.endsWith('/'))
    val key = name.toByteArray()
    val nameInArchive = key + '/'.code.toByte()

    buffer.clear()
    buffer.putInt(0x04034b50)
    // Version needed to extract (minimum)
    buffer.putShort(0x0014)
    // General purpose bit flag
    buffer.putShort(0x0800)
    // Compression method
    buffer.putShort(ZipEntry.STORED.toShort())
    // File last modification time
    buffer.putShort(0)
    // File last modification date
    buffer.putShort(0)
    // CRC-32 of uncompressed data
    buffer.putInt(0)
    // Compressed size
    buffer.putInt(0)
    // Uncompressed size
    buffer.putInt(0)
    // File name length
    buffer.putShort((nameInArchive.size and 0xffff).toShort())
    // Extra field length
    buffer.putShort(0)
    buffer.put(nameInArchive)

    buffer.flip()
    writeBuffer(buffer)

    writeCentralFileHeader(0, 0, ZipEntry.STORED, 0, nameInArchive, offset, dataOffset = -1, normalName = key, indexWriter = indexWriter)
  }

  fun writeRawEntry(
    header: ByteBuffer,
    content: ByteBuffer,
    name: ByteArray,
    size: Int,
    compressedSize: Int,
    method: Int,
    crc: Long,
    indexWriter: IkvIndexBuilder?,
  ) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channelPosition
    val dataOffset = offset + header.remaining()
    entryCount++
    assert(method != -1)

    writeBuffer(header)
    writeBuffer(content)

    writeCentralFileHeader(
      size = size,
      compressedSize = compressedSize,
      method = method,
      crc = crc,
      name = name,
      offset = offset,
      dataOffset = dataOffset,
      indexWriter = indexWriter,
    )
  }

  fun writeRawEntry(
    content: ByteBuffer,
    name: ByteArray,
    size: Int,
    compressedSize: Int,
    method: Int,
    crc: Long,
    headerSize: Int,
    indexWriter: IkvIndexBuilder?,
  ) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channelPosition
    entryCount++
    assert(method != -1)

    writeBuffer(content)
    writeCentralFileHeader(size, compressedSize, method, crc, name, offset, dataOffset = offset + headerSize, indexWriter = indexWriter)
  }

  fun writeEntryHeaderAt(
    name: ByteArray,
    header: ByteBuffer,
    position: Long,
    size: Int,
    compressedSize: Int,
    crc: Long,
    method: Int,
    indexWriter: IkvIndexBuilder?,
  ) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val dataOffset = position + header.remaining()

    if (fileChannel == null) {
      val c = channel as SeekableByteChannel
      c.position(position)
      do {
        c.write(header)
      }
      while (header.hasRemaining())
      c.position(channelPosition)
    }
    else {
      var currentPosition = position
      do {
        currentPosition += fileChannel.write(header, currentPosition)
      }
      while (header.hasRemaining())
    }

    entryCount++

    assert(channelPosition == dataOffset + compressedSize)
    writeCentralFileHeader(
      size = size,
      compressedSize = compressedSize,
      method = method,
      crc = crc,
      name = name,
      offset = position,
      dataOffset = dataOffset,
      indexWriter = indexWriter,
    )
  }

  private fun writeIndex(crc32: CRC32, indexWriter: IkvIndexBuilder): Int {
    // write one by one to channel to avoid buffer overflow
    indexWriter.write {
      crc32.update(it)
      it.flip()
      writeBuffer(it)
    }
    val indexDataEnd = channelPosition.toInt()

    fun writeData(task: (ByteBuffer) -> Unit) {
      buffer.clear()
      task(buffer)
      buffer.flip()

      crc32.update(buffer)
      buffer.flip()

      writeBuffer(buffer)
    }

    // write package class and resource hashes
    writeData { buffer ->
      val classPackages = indexWriter.classPackages
      val resourcePackages = indexWriter.resourcePackages
      if (classPackages.isEmpty() && resourcePackages.isEmpty()) {
        buffer.putInt(0)
        buffer.putInt(0)
      }
      else {
        val classPackageArray = indexWriter.classPackages.toLongArray()
        val resourcePackageArray = indexWriter.resourcePackages.toLongArray()

        // same content for same data
        classPackageArray.sort()
        resourcePackageArray.sort()

        buffer.putInt(classPackages.size)
        buffer.putInt(resourcePackages.size)
        useAsLongBuffer(buffer) {
          it.put(classPackageArray)
          it.put(resourcePackageArray)
        }
      }
    }

    // write names
    for (list in indexWriter.names.asSequence().chunked(4096)) {
      writeData { buffer ->
        val shortBuffer = buffer.asShortBuffer()
        for (name in list) {
          shortBuffer.put(name.size.toShort())
        }
        buffer.position(buffer.position() + (shortBuffer.position() * Short.SIZE_BYTES))
      }
    }

    for (list in indexWriter.names.asSequence().chunked(1024)) {
      writeData { buffer ->
        for (name in list) {
          buffer.put(name)
        }
      }
    }

    return indexDataEnd
  }

  internal fun finish(indexWriter: IkvIndexBuilder?) {
    if (finished) {
      throw IOException("This archive has already been finished")
    }

    val indexOffset: Int
    if (indexWriter != null && entryCount != 0) {
      indexOffset = writeIndexFile(indexWriter, INDEX_FILENAME.encodeToByteArray())
    }
    else {
      indexOffset = -1
    }

    val centralDirectoryOffset = channelPosition
    // write central directory file header
    metadataBuffer.flip()
    val centralDirectoryLength = metadataBuffer.limit()
    writeBuffer(metadataBuffer)

    buffer.clear()
    if (entryCount < 65_535) {
      // write an end of central directory record (EOCD)
      buffer.clear()
      buffer.putInt(ImmutableZipFile.EOCD)
      // write 0 to clear reused buffer content
      // number of this disk (short), disk where central directory starts (short)
      buffer.putInt(0)
      // number of central directory records on this disk
      val shortEntryCount = (entryCount.coerceAtMost(0xffff) and 0xffff).toShort()
      buffer.putShort(shortEntryCount)
      // total number of central directory records
      buffer.putShort(shortEntryCount)
      buffer.putInt(centralDirectoryLength)
      // central directory start offset, relative to start of archive
      buffer.putInt((centralDirectoryOffset and 0xffffffffL).toInt())

      // comment length
      if (indexWriter != null) {
        buffer.putShort((Byte.SIZE_BYTES + Integer.BYTES).toShort())
        // version
        buffer.put(INDEX_FORMAT_VERSION)
        buffer.putInt(indexOffset)
      }
      else {
        buffer.putShort(0)
      }
    }
    else {
      writeZip64End(
        centralDirectoryLength = centralDirectoryLength,
        centralDirectoryOffset = centralDirectoryOffset,
        optimizedMetadataOffset = indexOffset,
      )
    }
    buffer.flip()
    writeBuffer(buffer)

    finished = true
  }

  private fun writeIndexFile(indexWriter: IkvIndexBuilder, name: ByteArray): Int {
    // ditto on macOS doesn't like arbitrary data in zip file - wrap into zip entry
    val headerSize = 30 + name.size
    val headerPosition = getChannelPositionAndAdd(headerSize)
    val entryDataPosition = channelPosition

    val crc32 = CRC32()
    val indexOffset = writeIndex(crc32, indexWriter)

    val size = (channelPosition - entryDataPosition).toInt()
    val crc = crc32.value

    buffer.clear()
    writeLocalFileHeader(name = name, size = size, compressedSize = size, crc32 = crc, method = ZipEntry.STORED, buffer = buffer)
    buffer.flip()
    assert(buffer.remaining() == headerSize)
    writeEntryHeaderAt(
      name = name,
      header = buffer,
      position = headerPosition,
      size = size,
      compressedSize = size,
      crc = crc,
      method = ZipEntry.STORED,
      indexWriter = indexWriter,
    )
    return indexOffset
  }

  private fun writeZip64End(centralDirectoryLength: Int, centralDirectoryOffset: Long, optimizedMetadataOffset: Int) {
    val eocd64Position = channelPosition

    buffer.putInt(0x06064b50)
    // size of - will be written later
    val eocdSizePosition = buffer.position()
    buffer.position(eocdSizePosition + Long.SIZE_BYTES)
    // Version made by
    buffer.putShort(0)
    // Version needed to extract (minimum)
    buffer.putShort(0)
    // Disk number
    buffer.putInt(0)
    // Disk where the central directory starts
    buffer.putInt(0)
    // Number of central directory records on this disk
    buffer.putLong(entryCount.toLong())
    // Total number of central directory records
    buffer.putLong(entryCount.toLong())
    // Size of central directory (bytes)
    buffer.putLong(centralDirectoryLength.toLong())
    // central directory start offset, relative to start of archive
    buffer.putLong(centralDirectoryOffset)

    // comment length
    if (optimizedMetadataOffset != -1) {
      // version
      buffer.put(INDEX_FORMAT_VERSION)
      buffer.putInt(optimizedMetadataOffset)
    }

    buffer.putLong(eocdSizePosition, (buffer.position() - 12).toLong())

    // Zip64 end of central directory locator
    buffer.putInt(0x07064b50)
    // disk number with the start of the zip64 end of central directory
    buffer.putInt(0)
    // relative offset of the zip64 end of central directory record
    buffer.putLong(eocd64Position)
    // total number of disks
    buffer.putInt(1)

    // write EOCD (EOCD is required even if we write EOCD64)
    buffer.putInt(0x06054b50)
    // disk number (short)
    buffer.putShort(0xffff.toShort())
    // disk where the central directory starts (short)
    buffer.putShort(0xffff.toShort())
    // number of central directory records on this disk
    buffer.putShort(0xffff.toShort())
    // total number of central directory records
    buffer.putShort(0xffff.toShort())
    // Size of central directory (bytes) (or 0xffffffff for ZIP64)
    buffer.putInt(0xffffffff.toInt())
    // central directory offset start, relative to start of archive
    buffer.putInt(0xffffffff.toInt())
    // comment length
    buffer.putShort(0)
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

  internal fun writeBuffer(content: ByteBuffer) {
    if (fileChannel == null) {
      val size = content.remaining()
      do {
        channel.write(content)
      }
      while (content.hasRemaining())
      channelPosition += size
    }
    else {
      var currentPosition = channelPosition
      do {
        currentPosition += fileChannel.write(content, currentPosition)
      }
      while (content.hasRemaining())
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
      unmapBuffer(metadataBuffer)
      unmapBuffer(buffer)
    }
  }

  private fun writeCentralFileHeader(
    size: Int,
    compressedSize: Int,
    method: Int,
    crc: Long,
    name: ByteArray,
    offset: Long,
    dataOffset: Long,
    normalName: ByteArray = name,
    indexWriter: IkvIndexBuilder?,
  ) {
    var buffer = metadataBuffer
    if (buffer.remaining() < (46 + name.size)) {
      metadataBuffer = ByteBuffer.allocateDirect(buffer.capacity() * 2).order(ByteOrder.LITTLE_ENDIAN)
      buffer.flip()
      metadataBuffer.put(buffer)
      unmapBuffer(buffer)
      buffer = metadataBuffer
    }

    val headerOffset = buffer.position()
    buffer.putInt(headerOffset, 0x02014b50)
    // version made by
    buffer.putShort(headerOffset + 4, 0x0314)
    // version needed to extract (minimum)
    buffer.putShort(headerOffset + 6, 0x0014)
    // general purpose bit flag
    buffer.putShort(headerOffset + 8, 0x0800)
    // compression method
    buffer.putShort(headerOffset + 10, method.toShort())
    // CRC-32 of uncompressed data
    buffer.putInt(headerOffset + 16, (crc and 0xffffffffL).toInt())
    // compressed size
    buffer.putInt(headerOffset + 20, compressedSize)
    // uncompressed size
    buffer.putInt(headerOffset + 24, size)

    if (indexWriter != null) {
      indexWriter.add(indexWriter.entry(offset = dataOffset, size = size, key = Xxh3.hash(normalName)))
      indexWriter.names.add(normalName)
    }

    // file name length
    buffer.putShort(headerOffset + 28, (name.size and 0xffff).toShort())
    // external file attributes
    val isDir = name.lastOrNull() == '/'.code.toByte()
    val unixAttributes = if (isDir) 0x41ed0000L else 0x81a40000L
    buffer.putInt(headerOffset + 38, unixAttributes.toInt())
    // relative offset of local file header
    buffer.putInt(headerOffset + 42, (offset and 0xffffffffL).toInt())
    // file name
    buffer.position(headerOffset + 46)
    buffer.put(name)
  }
}

internal fun writeLocalFileHeader(name: ByteArray, size: Int, compressedSize: Int, crc32: Long, method: Int, buffer: ByteBuffer): Int {
  buffer.putInt(0x04034b50)
  // Version needed to extract (minimum)
  buffer.putShort(0x0014)
  // General purpose bit flag
  buffer.putShort(0x0800)
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