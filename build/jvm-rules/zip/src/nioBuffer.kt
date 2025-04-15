// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry

internal fun copyFromFileChannelToBuffer(sourceChannel: FileChannel, buffer: ByteBuffer, size: Long, file: Path?) {
  var currentPosition = 0L
  while (currentPosition < size) {
    val bytesRead = sourceChannel.read(buffer, currentPosition)
    if (bytesRead == -1) {
      throw EOFException("Unexpected end of file while reading from FileChannel(file=$file, size=$size) to buffer($buffer) " +
                         "Read $currentPosition bytes so far.")
    }
    currentPosition += bytesRead
  }
}

internal fun CRC32.compute(data: ByteArray): Long {
  reset()
  update(data)
  return value
}

internal fun CRC32.compute(data: ByteBuffer): Long {
  reset()
  data.mark()
  update(data)
  data.reset()
  return value
}

internal inline fun writeNioBuffer(
  path: ByteArray,
  crc32: CRC32?,
  localFileHeaderOffset: Long,
  buffer: ByteBuffer,
  zipIndexWriter: ZipIndexWriter,
  task: (ByteBuffer) -> Unit,
) {
  val headerSize = 30 + path.size
  val headerPosition = buffer.position()
  val endOfHeaderPosition = headerPosition + headerSize
  buffer.position(endOfHeaderPosition)

  task(buffer)

  val size = buffer.position() - endOfHeaderPosition

  val crc = if (crc32 == null || size == 0) {
    0
  }
  else {
    val oldPosition = buffer.position()
    val oldLimit = buffer.limit()
    buffer.position(endOfHeaderPosition)
    buffer.limit(oldPosition)
    crc32.reset()
    crc32.update(buffer)
    // oldPosition is not restored - see below, we set it to headerPosition
    buffer.limit(oldLimit)
    crc32.value
  }

  buffer.position(headerPosition)
  writeZipLocalFileHeader(path = path, size = size, compressedSize = size, crc32 = crc, method = ZipEntry.STORED, buffer = buffer)
  assert(buffer.position() == endOfHeaderPosition)

  zipIndexWriter.writeCentralFileHeader(path = path, size = size, crc = crc, headerOffset = localFileHeaderOffset)
}