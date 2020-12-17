// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SeekableByteChannel
import java.util.zip.ZipEntry

internal class ZipArchiveOutputStream(private val channel: SeekableByteChannel) : Closeable {
  private var finished = false

  internal val entries = ArrayList<EntryMetaData>()

  // 128K should be enough (max size for central directory record = 46 + file name length)
  private val buffer = ByteBuffer.allocate(131_072).order(ByteOrder.LITTLE_ENDIAN)

  fun addDirEntry(name: String) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    val entry = ZipEntry("$name/")
    entry.method = ZipEntry.STORED
    entry.size = 0
    entry.compressedSize = 0

    val nameBytes = entry.name.toByteArray()
    entries.add(EntryMetaData(offset = channel.position(), entry = entry, name = nameBytes))

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
    buffer.putShort((nameBytes.size and 0xffff).toShort())
    // Extra field length
    buffer.putShort(0)
    buffer.put(nameBytes)

    buffer.flip()
    writeBuffer(buffer)
  }

  @Suppress("DuplicatedCode")
  fun addRawArchiveEntry(entry: ZipEntry, content: ByteBuffer, nameBytes: ByteArray) {
    if (finished) {
      throw IOException("Stream has already been finished")
    }

    entries.add(EntryMetaData(offset = channel.position(), entry = entry, name = nameBytes))
    assert(entry.method != -1)
    if ((entry.size >= 0xFFFFFFFFL || entry.compressedSize >= 0xFFFFFFFFL)) {
      throw UnsupportedOperationException("Entry is too big")
    }

    writeBuffer(content)
  }

  fun finish(comment: ByteBuffer?) {
    if (finished) {
      throw IOException("This archive has already been finished")
    }

    val centralDirectoryOffset = channel.position()

    // write central directory file header
    buffer.clear()
    var centralDirectoryLength = 0
    for (entry in entries) {
      createCentralFileHeader(entry, buffer)
      if (buffer.remaining() < 4_096) {
        centralDirectoryLength += buffer.position()
        buffer.flip()
        writeBuffer(buffer)
        buffer.clear()
      }
    }

    centralDirectoryLength += buffer.position()

    // write end of central directory record (EOCD)
    buffer.putInt(0x06054b50)
    // write 0 to clear reused buffer content
    // Number of this disk
    buffer.putShort(0)
    // Disk where central directory starts
    buffer.putShort(0)
    // Number of central directory records on this disk
    val shortEntryCount = (entries.size.coerceAtMost(0xffff) and 0xffff).toShort()
    buffer.putShort(shortEntryCount)
    // Total number of central directory records
    buffer.putShort(shortEntryCount)
    buffer.putInt(centralDirectoryLength)
    // Offset of start of central directory, relative to start of archive
    buffer.putInt((centralDirectoryOffset and 0xffffffffL).toInt())
    // Comment length
    if (comment == null) {
      buffer.putShort(0)
    }
    else {
      buffer.putShort((comment.remaining() and 0xffff).toShort())
      // assume that comment is small and will fit into our own buffer
      buffer.put(comment)
    }

    buffer.flip()
    writeBuffer(buffer)

    entries.clear()
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

  @Suppress("DuplicatedCode")
  private fun createCentralFileHeader(item: EntryMetaData, buffer: ByteBuffer) {
    val entry = item.entry

    buffer.putInt(0x02014b50)
    // write 0 to clear reused buffer content
    // Version made by
    buffer.putShort(0)
    // Version needed to extract (minimum)
    buffer.putShort(0)
    // General purpose bit flag
    buffer.putShort(0)
    // Compression method
    buffer.putShort(entry.method.toShort())

    // File last modification time
    buffer.putShort(0)
    // File last modification date
    buffer.putShort(0)

    // CRC-32 of uncompressed data
    buffer.putInt((entry.crc and 0xffffffffL).toInt())
    // Compressed size
    buffer.putInt(entry.compressedSize.toInt())
    // Uncompressed size
    buffer.putInt((entry.size and 0xffffffffL).toInt())

    // File name length
    buffer.putShort((item.name.size and 0xffff).toShort())
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
    buffer.putInt((item.offset and 0xffffffffL).toInt())
    // File name
    buffer.put(item.name)
  }
}

internal class EntryMetaData(val offset: Long, val entry: ZipEntry, val name: ByteArray)