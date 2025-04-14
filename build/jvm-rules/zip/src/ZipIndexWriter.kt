// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.dynatrace.hash4j.hashing.Hashing
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import java.util.zip.ZipEntry

internal const val INDEX_FORMAT_VERSION: Byte = 4

class ZipIndexWriter(@JvmField val packageIndexBuilder: PackageIndexBuilder?, allocator: ByteBufAllocator = byteBufferAllocator) {
  private var buffer: ByteBuf? = allocator.directBuffer(64 * 1024)

  private var entryCount = 0

  @Synchronized
  fun isEmpty(): Boolean = entryCount == 0

  fun writeCentralFileHeader(path: ByteArray, size: Int, crc: Long, headerOffset: Long) {
    writeCentralFileHeader(
      path = path,
      size = size,
      compressedSize = size,
      method = ZipEntry.STORED,
      crc = crc,
      headerOffset = headerOffset,
      dataOffset = 30 + path.size + headerOffset,
    )
  }

  @Synchronized
  fun writeCentralFileHeader(path: ByteArray, size: Int, compressedSize: Int, method: Int, crc: Long, headerOffset: Long, dataOffset: Long) {
    val buffer = buffer!!
    entryCount++
    buffer.ensureWritable(46 + path.size)

    buffer.writeIntLE(0x02014b50)
    // Version made by (2), Version needed to extract (2), General purpose bit flag (2)
    buffer.writeZero(6)
    // compression method
    buffer.writeShortLE(method)
    // File last modification time (2), File last modification date(2)
    buffer.writeZero(4)
    // CRC-32 of uncompressed data
    buffer.writeIntLE((crc and 0xffffffffL).toInt())
    // compressed size
    buffer.writeIntLE(compressedSize)
    // uncompressed size
    buffer.writeIntLE(size)

    val indexWriter = packageIndexBuilder?.indexWriter
    if (indexWriter != null) {
      indexWriter.add(IkvIndexEntry(longKey = Hashing.xxh3_64().hashBytesToLong(path), offset = dataOffset, size = size))
      indexWriter.names.add(path)
    }

    // file name length
    buffer.writeShortLE((path.size and 0xffff))
    // Extra field length (2), File comment length (2), Disk number where a file starts (or 0xffff for ZIP64)(2), Internal file attributes (2), External file attributes(4)
    buffer.writeZero(12)
    // relative offset of local file header
    buffer.writeIntLE((headerOffset and 0xffffffffL).toInt())
    // file name
    buffer.writeBytes(path)
  }

  @Synchronized
  internal fun writeCentralFileHeaderForDirectory(path: ByteArray, headerOffset: Long) {
    val buffer = buffer!!
    entryCount++
    val nameSize = path.size + 1
    buffer.ensureWritable(46 + nameSize)

    buffer.writeIntLE(0x02014b50)
    // Version made by (2), Version needed to extract (2), General purpose bit flag (2), compression method
    // File last modification time (2), File last modification date(2), CRC-32 of uncompressed data
    // compressed size, uncompressed size
    buffer.writeZero(24)
    // file name length
    buffer.writeShortLE((nameSize and 0xffff))
    // Extra field length (2), File comment length (2), Disk number where a file starts (or 0xffff for ZIP64)(2),
    // Internal file attributes (2), External file attributes(4)
    buffer.writeZero(12)
    // relative offset of local file header
    buffer.writeIntLE((headerOffset and 0xffffffffL).toInt())
    // file name
    buffer.writeBytes(path)
    buffer.writeByte('/'.code)

    val indexWriter = packageIndexBuilder?.indexWriter
    if (indexWriter != null) {
      indexWriter.add(IkvIndexEntry(longKey = Hashing.xxh3_64().hashBytesToLong(path), offset = -1, size = 0))
      indexWriter.names.add(path)
    }
  }

  @Synchronized
  fun finish(centralDirectoryOffset: Long, indexDataEnd: Int): ByteBuf {
    val buffer = buffer!!
    val centralDirectoryLength = buffer.readableBytes()
    if (entryCount < 65_535) {
      // write an end of central directory record (EOCD)
      buffer.writeIntLE(0x6054B50)
      // number of this disk (short), disk where central directory starts (short)
      buffer.writeZero(4)
      // number of central directory records on this disk
      val shortEntryCount = (entryCount.coerceAtMost(0xffff) and 0xffff)
      buffer.writeShortLE(shortEntryCount)
      // total number of central directory records
      buffer.writeShortLE(shortEntryCount)
      buffer.writeIntLE(centralDirectoryLength)
      // central directory start offset, relative to start of archive
      buffer.writeIntLE((centralDirectoryOffset and 0xffffffffL).toInt())

      // comment length
      val indexWriter = packageIndexBuilder?.indexWriter
      if (indexWriter == null) {
        buffer.writeZero(2)
      }
      else {
        buffer.writeShortLE(Byte.SIZE_BYTES + Integer.BYTES)
        // version
        buffer.writeByte(INDEX_FORMAT_VERSION.toInt())
        buffer.writeIntLE(indexDataEnd)
      }
    }
    else {
      writeZip64End(
        buffer = buffer,
        entryCount = entryCount,
        centralDirectoryLength = centralDirectoryLength,
        centralDirectoryOffset = centralDirectoryOffset,
        optimizedMetadataOffset = indexDataEnd,
      )
    }
    return buffer
  }

  @Synchronized
  fun release() {
    buffer?.let {
      buffer = null
      it.release()
    }
  }
}

private fun writeZip64End(
  entryCount: Int,
  centralDirectoryLength: Int,
  centralDirectoryOffset: Long,
  optimizedMetadataOffset: Int,
  buffer: ByteBuf,
) {
  buffer.writeIntLE(0x06064b50)
  // size of - will be written later
  val eocdSizePosition = buffer.writerIndex()
  buffer.writeLongLE(0)
  val eocdSizeCalculationStartPosition = buffer.writerIndex()
  // Version made by
  buffer.writeShortLE(0)
  // Version needed to extract (minimum)
  buffer.writeShortLE(0)
  // Disk number
  buffer.writeIntLE(0)
  // Disk where the central directory starts
  buffer.writeIntLE(0)
  // Number of central directory records on this disk
  buffer.writeLongLE(entryCount.toLong())
  // Total number of central directory records
  buffer.writeLongLE(entryCount.toLong())
  // Size of central directory (bytes)
  buffer.writeLongLE(centralDirectoryLength.toLong())
  // central directory start offset, relative to start of archive
  buffer.writeLongLE(centralDirectoryOffset)

  // comment length
  if (optimizedMetadataOffset != -1) {
    // version
    buffer.writeByte(INDEX_FORMAT_VERSION.toInt())
    buffer.writeIntLE(optimizedMetadataOffset)
  }

  buffer.setLongLE(eocdSizePosition, (buffer.writerIndex() - eocdSizeCalculationStartPosition).toLong())

  // Zip64 end of central directory locator
  buffer.writeIntLE(0x07064b50)
  // disk number with the start of the zip64 end of central directory
  buffer.writeIntLE(0)
  // relative offset of the zip64 end of central directory record
  buffer.writeLongLE(centralDirectoryOffset + centralDirectoryLength)
  // total number of disks
  buffer.writeIntLE(1)

  // write EOCD (EOCD is required even if we write EOCD64)
  buffer.writeIntLE(0x06054b50)
  // disk number (short)
  buffer.writeShortLE(0xffff)
  // disk where the central directory starts (short)
  buffer.writeShortLE(0xffff)
  // number of central directory records on this disk
  buffer.writeShortLE(0xffff)
  // total number of central directory records
  buffer.writeShortLE(0xffff)
  // Size of central directory (bytes) (or 0xffffffff for ZIP64)
  buffer.writeIntLE(0xffffffff.toInt())
  // central directory offset start, relative to start of archive
  buffer.writeIntLE(0xffffffff.toInt())
  // comment length
  buffer.writeShortLE(0)
}
