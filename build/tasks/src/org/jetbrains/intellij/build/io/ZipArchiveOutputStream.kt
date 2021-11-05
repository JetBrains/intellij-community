// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")
package org.jetbrains.intellij.build.io

import com.intellij.util.io.Murmur3_32Hash
import it.unimi.dsi.fastutil.ints.IntArrayList
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.zip.ZipEntry

internal class ZipArchiveOutputStream(private val channel: WritableByteChannel, private val withOptimizedMetadataEnabled: Boolean) : Closeable {
  private var finished = false
  private var entryCount = 0

  private val metadataBuffer = ByteBuffer.allocateDirect(12 * 1024 * 1024).order(ByteOrder.LITTLE_ENDIAN)
  // 1 MB should be enough for end of central directory record
  private val buffer = ByteBuffer.allocateDirect(1024 * 1024).order(ByteOrder.LITTLE_ENDIAN)

  private val sizes = IntArrayList()
  private val names = ArrayList<ByteArray>()
  private val dataOffsets = IntArrayList()

  private var channelPosition = 0L

  private val fileChannel = channel as? FileChannel

  fun addDirEntry(name: ByteArray) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channelPosition
    entryCount++

    buffer.clear()
    buffer.putInt(0x04034b50)
    // Version needed to extract (minimum)
    buffer.putShort(0)
    // General purpose bit flag
    buffer.putShort(0)
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
    buffer.putShort((name.size and 0xffff).toShort())
    // Extra field length
    buffer.putShort(0)
    buffer.put(name)

    buffer.flip()
    writeBuffer(buffer)

    writeCentralFileHeader(0, 0, ZipEntry.STORED, 0, name, offset, dataOffset = 0)
  }

  fun writeRawEntry(header: ByteBuffer, content: ByteBuffer, name: ByteArray, size: Int, compressedSize: Int, method: Int, crc: Long) {
    @Suppress("DuplicatedCode")
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channelPosition
    val dataOffset = offset.toInt() + header.remaining()
    entryCount++
    assert(method != -1)

    writeBuffer(header)
    writeBuffer(content)

    writeCentralFileHeader(size, compressedSize, method, crc, name, offset, dataOffset = dataOffset)
  }

  fun writeRawEntry(content: ByteBuffer, name: ByteArray, size: Int, compressedSize: Int, method: Int, crc: Long, headerSize: Int) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channelPosition
    entryCount++
    assert(method != -1)

    writeBuffer(content)
    writeCentralFileHeader(size, compressedSize, method, crc, name, offset, dataOffset = offset.toInt() + headerSize)
  }

  fun writeEntryHeaderAt(name: ByteArray, header: ByteBuffer, position: Long, size: Int, compressedSize: Int, crc: Long, method: Int) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

      val dataOffset = position.toInt() + header.remaining()

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

    assert(channelPosition == (dataOffset + compressedSize).toLong())
    writeCentralFileHeader(size = size,
                           compressedSize = compressedSize,
                           method = method,
                           crc = crc,
                           name = name,
                           offset = position,
                           dataOffset = dataOffset)
  }

  private fun writeCustomMetadata(): Int {
    val optimizedMetadataOffset = channelPosition.toInt()
    // write one by one to channel to avoid buffer overflow
    writeIntArray(sizes.toIntArray())
    writeIntArray(dataOffsets.toIntArray())
    writeIntArray(computeTableIndexes(names))
    return optimizedMetadataOffset
  }

  private fun writeIntArray(value: IntArray) {
    buffer.clear()
    buffer.asIntBuffer().put(value)
    buffer.limit(value.size * Int.SIZE_BYTES)
    writeBuffer(buffer)
  }

  fun finish() {
    if (finished) {
      throw IOException("This archive has already been finished")
    }

    val optimizedMetadataOffset = if (withOptimizedMetadataEnabled) writeCustomMetadata() else -1

    val centralDirectoryOffset = channelPosition
    // write central directory file header
    metadataBuffer.flip()
    val centralDirectoryLength = metadataBuffer.limit()
    writeBuffer(metadataBuffer)

    // write end of central directory record (EOCD)
    buffer.clear()
    buffer.putInt(0x06054b50)
    // write 0 to clear reused buffer content
    // number of this disk (short), disk where central directory starts (short)
    buffer.putInt(0)
    // number of central directory records on this disk
    val shortEntryCount = (entryCount.coerceAtMost(0xffff) and 0xffff).toShort()
    buffer.putShort(shortEntryCount)
    // total number of central directory records
    buffer.putShort(shortEntryCount)
    buffer.putInt(centralDirectoryLength)
    // Offset of start of central directory, relative to start of archive
    buffer.putInt((centralDirectoryOffset and 0xffffffffL).toInt())

    // comment length
    if (withOptimizedMetadataEnabled) {
      buffer.putShort(1 + 4 + 4)
      // version
      buffer.put(1)
      buffer.putInt(sizes.size)
      buffer.putInt(optimizedMetadataOffset)
    }
    else {
      buffer.putShort(0)
    }

    buffer.flip()
    writeBuffer(buffer)

    finished = true
  }

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
    if (!finished) {
      channel.use {
        finish()
      }
    }
  }

  private fun writeCentralFileHeader(size: Int, compressedSize: Int, method: Int, crc: Long, name: ByteArray, offset: Long, dataOffset: Int) {
    val buffer = metadataBuffer
    val headerOffset = buffer.position()
    buffer.putInt(headerOffset, 0x02014b50)
    // compression method
    buffer.putShort(headerOffset + 10, method.toShort())
    // CRC-32 of uncompressed data
    buffer.putInt(headerOffset + 16, (crc and 0xffffffffL).toInt())
    // compressed size
    buffer.putInt(headerOffset + 20, compressedSize)
    // uncompressed size
    buffer.putInt(headerOffset + 24, size)

    sizes.add(size)
    dataOffsets.add(dataOffset)
    names.add(name)

    // file name length
    buffer.putShort(headerOffset + 28, (name.size and 0xffff).toShort())
    // relative offset of local file header
    buffer.putInt(headerOffset + 42, (offset and 0xffffffffL).toInt())
    // file name
    buffer.position(headerOffset + 46)
    buffer.put(name)
  }
}

private fun computeTableIndexes(names: List<ByteArray>): IntArray {
  val indexes = IntArray(names.size)
  val tableSize = names.size * 2
  val indexToName = arrayOfNulls<ByteArray>(tableSize)
  @Suppress("ReplaceManualRangeWithIndicesCalls")
  for (entryIndex in 0 until names.size) {
    val name = names.get(entryIndex)
    val nameHash = Murmur3_32Hash.MURMUR3_32.hashBytes(name, 0, name.size - (if (name.last() == '/'.code.toByte()) 1 else 0))
    var index = Math.floorMod(nameHash, tableSize)
    while (true) {
      val found = indexToName[index]
      if (found == null) {
        indexes[entryIndex] = index
        indexToName[index] = name
        break
      }
      else if (name.contentEquals(indexToName[index])) {
        indexes[entryIndex] = index
        break
      }
      else if (++index == tableSize) {
        index = 0
      }
    }
  }
  return indexes
}