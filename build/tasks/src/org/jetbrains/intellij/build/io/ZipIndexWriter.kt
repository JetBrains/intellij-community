// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.util.lang.ImmutableZipFile
import com.intellij.util.lang.ImmutableZipFile.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE
import com.intellij.util.lang.Xxh3
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator

internal const val INDEX_FORMAT_VERSION: Byte = 4

class ZipIndexWriter(@JvmField val indexWriter: IkvIndexBuilder?) {
  @JvmField
  val buffer: ByteBuf = ByteBufAllocator.DEFAULT.directBuffer(512 * 1024)

  var entryCount = 0
    private set

  private var isReleased = false

  fun writeCentralFileHeader(
    size: Int,
    compressedSize: Int,
    method: Int,
    crc: Long,
    name: ByteArray,
    localFileHeaderOffset: Long,
    dataOffset: Long,
    normalName: ByteArray = name,
  ) {
    entryCount++
    buffer.ensureWritable(46 + name.size)

    buffer.writeIntLE(CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE)
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

    if (indexWriter != null) {
      indexWriter.add(IkvIndexEntry(longKey = Xxh3.hash(normalName), offset = dataOffset, size = size))
      indexWriter.names.add(normalName)
    }

    // file name length
    buffer.writeShortLE((name.size and 0xffff))
    // Extra field length (2), File comment length (2), Disk number where a file starts (or 0xffff for ZIP64)(2), Internal file attributes (2), External file attributes(4)
    buffer.writeZero(12)
    // relative offset of local file header
    buffer.writeIntLE((localFileHeaderOffset and 0xffffffffL).toInt())
    // file name
    buffer.writeBytes(name)
  }

  fun finish(centralDirectoryOffset: Long, indexWriter: IkvIndexBuilder?, indexOffset: Int) {
    val centralDirectoryLength = buffer.readableBytes()
    if (entryCount < 65_535) {
      // write an end of central directory record (EOCD)
      buffer.writeIntLE(ImmutableZipFile.EOCD)
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
      if (indexWriter == null) {
        buffer.writeZero(2)
      }
      else {
        buffer.writeShortLE(Byte.SIZE_BYTES + Integer.BYTES)
        // version
        buffer.writeByte(INDEX_FORMAT_VERSION.toInt())
        buffer.writeIntLE(indexOffset)
      }
    }
    else {
      writeZip64End(
        entryCount = entryCount,
        centralDirectoryLength = centralDirectoryLength,
        centralDirectoryOffset = centralDirectoryOffset,
        optimizedMetadataOffset = indexOffset,
      )
    }
  }

  private fun writeZip64End(
    entryCount: Int,
    centralDirectoryLength: Int,
    centralDirectoryOffset: Long,
    optimizedMetadataOffset: Int,
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

  fun release() {
    if (!isReleased) {
      buffer.release()
      isReleased = true
    }
  }
}

