// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.ZipEntry

internal class ZipArchiveOutputStream(private val channel: FileChannel) : Closeable {
  private var finished = false
  private var entryCount = 0

  private val metadataBuffer = ByteBuffer.allocateDirect(5 * 1024 * 1024).order(ByteOrder.LITTLE_ENDIAN)
  // 128K should be enough for end of central directory record
  private val buffer = ByteBuffer.allocateDirect(16_384).order(ByteOrder.LITTLE_ENDIAN)

  private val tempArray = arrayOfNulls<ByteBuffer>(2)

  fun addDirEntry(name: ByteArray) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channel.position()
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

    writeCentralFileHeader(0, 0, ZipEntry.STORED, 0, metadataBuffer, name, offset)
  }

  fun writeRawEntry(header: ByteBuffer, content: ByteBuffer, name: ByteArray, size: Int, compressedSize: Int, method: Int, crc: Long) {
    @Suppress("DuplicatedCode")
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channel.position()
    entryCount++
    assert(method != -1)
    if (size >= 0xFFFFFFFFL || compressedSize >= 0xFFFFFFFFL) {
      throw UnsupportedOperationException("Entry is too big")
    }

    tempArray[0] = header
    tempArray[1] = content
    do {
      channel.write(tempArray, 0, 2)
    }
    while (header.hasRemaining() || content.hasRemaining())

    writeCentralFileHeader(size, compressedSize, method, crc, metadataBuffer, name, offset)
  }

  fun writeRawEntry(content: ByteBuffer, name: ByteArray, size: Int, compressedSize: Int, method: Int, crc: Long) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val offset = channel.position()
    entryCount++
    assert(method != -1)
    if (size >= 0xFFFFFFFFL || compressedSize >= 0xFFFFFFFFL) {
      throw UnsupportedOperationException("Entry is too big")
    }

    writeBuffer(content)
    writeCentralFileHeader(size, compressedSize, method, crc, metadataBuffer, name, offset)
  }

  fun finish(comment: ByteBuffer?) {
    if (finished) {
      throw IOException("This archive has already been finished")
    }

    val centralDirectoryOffset = channel.position()
    // write central directory file header
    metadataBuffer.flip()
    val centralDirectoryLength = metadataBuffer.limit()
    writeBuffer(metadataBuffer)

    // write end of central directory record (EOCD)
    buffer.clear()
    buffer.putInt(0x06054b50)
    // write 0 to clear reused buffer content
    // Number of this disk
    buffer.putShort(0)
    // Disk where central directory starts
    buffer.putShort(0)
    // Number of central directory records on this disk
    val shortEntryCount = (entryCount.coerceAtMost(0xffff) and 0xffff).toShort()
    buffer.putShort(shortEntryCount)
    // Total number of central directory records
    buffer.putShort(shortEntryCount)
    buffer.putInt(centralDirectoryLength)
    // Offset of start of central directory, relative to start of archive
    buffer.putInt((centralDirectoryOffset and 0xffffffffL).toInt())
    // Comment length
    if (comment == null) {
      buffer.putShort(0)
      buffer.flip()
      writeBuffer(buffer)
    }
    else {
      buffer.putShort((comment.remaining() and 0xffff).toShort())
      buffer.flip()
      writeBuffer(buffer)
      writeBuffer(comment)
    }

    finished = true
  }

  private fun writeBuffer(content: ByteBuffer) {
    do {
      channel.write(content)
    }
    while (content.hasRemaining())
  }

  override fun close() {
    if (!finished) {
      channel.use {
        finish(null)
      }
    }
  }
}

private fun writeCentralFileHeader(size: Int, compressedSize: Int, method: Int, crc: Long, buffer: ByteBuffer, name: ByteArray, offset: Long) {
  buffer.putInt(0x02014b50)
  // write 0 to clear reused buffer content
  // Version made by
  buffer.putShort(0)
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
  buffer.putInt((crc and 0xffffffffL).toInt())
  // Compressed size
  buffer.putInt(compressedSize)
  // Uncompressed size
  buffer.putInt(size)

  // File name length
  buffer.putShort((name.size and 0xffff).toShort())
  // Extra field length
  buffer.putShort(0)
  // File comment length
  buffer.putShort(0)
  // Disk number where file starts
  buffer.putShort(0)
  // Internal file attributes
  buffer.putShort(0)
  // External file attributes
  buffer.putInt(0)
  // Relative offset of local file header
  buffer.putInt((offset and 0xffffffffL).toInt())
  // File name
  buffer.put(name)
}