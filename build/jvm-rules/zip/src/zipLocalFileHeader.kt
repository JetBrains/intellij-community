// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry

fun writeZipLocalFileHeader(path: ByteArray, size: Int, crc32: Long, buffer: ByteBuf) {
  writeZipLocalFileHeader(path = path, size = size, compressedSize = size, crc32 = crc32, method = ZipEntry.STORED, buffer = buffer)
}

internal fun writeZipLocalFileHeader(path: ByteArray, size: Int, compressedSize: Int, crc32: Long, method: Int, buffer: ByteBuf) {
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
  buffer.writeShortLE(path.size and 0xffff)
  // Extra field length (2)
  buffer.writeZero(2)
  buffer.writeBytes(path)
}

internal fun writeZipLocalFileHeader(path: ByteArray, size: Int, crc32: Long, buffer: ByteBuffer) {
  writeZipLocalFileHeader(path = path, size = size, compressedSize = size, crc32 = crc32, method = ZipEntry.STORED, buffer = buffer)
}

internal fun writeZipLocalFileHeader(
  path: ByteArray,
  size: Int,
  compressedSize: Int,
  crc32: Long,
  method: Int,
  buffer: ByteBuffer,
) {
  assert(buffer.order() == ByteOrder.LITTLE_ENDIAN)
  buffer.putInt(0x04034b50) // Local file header signature
  buffer.putShort(0) // Version needed to extract
  buffer.putShort(0) // General purpose bit flag
  buffer.putShort(method.toShort()) // Compression method
  buffer.putInt(0) // Last modification time and date
  buffer.putInt((crc32 and 0xffffffffL).toInt()) // CRC-32
  buffer.putInt(compressedSize) // Compressed size
  buffer.putInt(size) // Uncompressed size
  buffer.putShort(path.size.toShort()) // File name length
  buffer.putShort(0) // Extra field length
  buffer.put(path) // Write file name
}

internal fun writeDirEntriesUsingNioBuffer(
  names: Collection<String>,
  channelPosition: Long,
  dataWriter: DataWriter,
  zipIndexWriter: ZipIndexWriter,
): Long {
  var position = channelPosition
  for (name in names) {
    position += writeDirEntryUsingNioBuffer(name, position, zipIndexWriter) { headerSize, position ->
      dataWriter.asNioBuffer(headerSize, position)!!
    }
  }
  return position
}

internal fun writeDirEntriesUsingNioBuffer(
  names: Array<String>,
  channelPosition: Long,
  dataWriter: DataWriter,
  zipIndexWriter: ZipIndexWriter,
): Long {
  var position = channelPosition
  for (name in names) {
    position += writeDirEntryUsingNioBuffer(name, position, zipIndexWriter) { headerSize, position ->
      dataWriter.asNioBuffer(headerSize, position)!!
    }
  }
  return position
}

private inline fun writeDirEntryUsingNioBuffer(
  name: String,
  position: Long,
  zipIndexWriter: ZipIndexWriter,
  nioBuffer: (headerSize: Int, position: Long) -> ByteBuffer,
): Int {
  assert(!name.endsWith('/'))
  val key = name.toByteArray()
  val nameSize = key.size + 1
  val headerSize = 30 + nameSize
  val buffer = nioBuffer(headerSize, position)
  assert(buffer.order() == ByteOrder.LITTLE_ENDIAN)

  buffer.putInt(0x04034b50)
  buffer.putInt(0) // Version needed to extract (minimum), General purpose bit flag
  buffer.putShort(0) // Compression method
  buffer.putLong(0) // File last modification time, File last modification date, CRC-32 of uncompressed data
  // Compressed size, Uncompressed size
  buffer.putLong(0)
  buffer.putShort(nameSize.toShort()) // File name length
  buffer.putShort(0) // Extra field length
  buffer.put(key)
  buffer.put('/'.code.toByte())

  zipIndexWriter.writeCentralFileHeaderForDirectory(path = key, headerOffset = position)

  return headerSize
}

internal fun writeDirEntries(names: Array<String>, channelPosition: Long, zipIndexWriter: ZipIndexWriter, buffer: ByteBuf) {
  var position = channelPosition
  for (name in names) {
    position += writeDirEntry(name, buffer, zipIndexWriter, position)
  }
}

internal fun writeDirEntries(names: Collection<String>, channelPosition: Long, zipIndexWriter: ZipIndexWriter, buffer: ByteBuf) {
  var position = channelPosition
  for (name in names) {
    position += writeDirEntry(name, buffer, zipIndexWriter, position)
  }
}

private fun writeDirEntry(name: String, buffer: ByteBuf, zipIndexWriter: ZipIndexWriter, position: Long): Int {
  assert(!name.endsWith('/'))
  val key = name.toByteArray()
  val nameSize = key.size + 1
  val headerSize = 30 + nameSize
  buffer.ensureWritable(headerSize)

  buffer.writeIntLE(0x04034b50)

  // Version needed to extract (minimum), General purpose bit flag, Compression method,
  // File last modification time, File last modification date, CRC-32 of uncompressed data,
  // Compressed size, Uncompressed size
  buffer.writeZero(22)
  buffer.writeShortLE(nameSize) // File name length
  buffer.writeShortLE(0) // Extra field length
  buffer.writeBytes(key)
  buffer.writeByte('/'.code)

  zipIndexWriter.writeCentralFileHeaderForDirectory(path = key, headerOffset = position)
  return headerSize
}