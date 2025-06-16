// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import java.util.zip.ZipException
import kotlin.experimental.and

typealias EntryProcessor = (String, () -> ByteBuffer) -> ZipEntryProcessorResult


enum class ZipEntryProcessorResult {
  CONTINUE,
  STOP,
}

// only files are processed
fun readZipFile(file: Path, entryProcessor: EntryProcessor) {
  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  try {
    mapFileAndUse(file) { buffer, fileSize ->
      readZipEntries(buffer = buffer, fileSize = fileSize, entryProcessor = entryProcessor)
    }
  }
  catch (e: IOException) {
    throw IOException("Cannot read $file", e)
  }
}

suspend fun suspendAwareReadZipFile(file: Path, entryProcessor: suspend (String, () -> ByteBuffer) -> Unit) {
  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  mapFileAndUse(file) { buffer, fileSize ->
    readZipEntries(buffer = buffer, fileSize = fileSize, entryProcessor = { it, name ->
      entryProcessor(it, name)
      ZipEntryProcessorResult.CONTINUE
    })
  }
}

private inline fun mapFileAndUse(file: Path, consumer: (ByteBuffer, fileSize: Int) -> Unit) {
  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  var fileSize: Int
  var mappedBuffer: ByteBuffer
  FileChannel.open(file, EnumSet.of(StandardOpenOption.READ)).use { fileChannel ->
    fileSize = fileChannel.size().toInt()
    mappedBuffer = try {
      fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize.toLong())
    }
    catch (_: UnsupportedOperationException) {
      // in memory fs
      val buffer = ByteBuffer.allocate(fileSize)
      while (buffer.hasRemaining()) {
        fileChannel.read(buffer)
      }
      buffer.rewind()
      buffer
    }
    mappedBuffer.order(ByteOrder.LITTLE_ENDIAN)
  }

  try {
    consumer(mappedBuffer, fileSize)
  }
  finally {
    if (mappedBuffer.isDirect) {
      // on Windows, a memory-mapped file cannot be deleted without clearing the in-memory buffer first
      unmapBuffer(mappedBuffer)
    }
  }
}

internal inline fun readCentralDirectory(
  buffer: ByteBuffer,
  centralDirPosition: Int,
  centralDirSize: Int,
  entryProcessor: EntryProcessor,
  byteBufferAllocator: SingleByteBufferAllocator,
) {
  var offset = centralDirPosition

  // assume that the file name is not greater than ~2 KiB
  // JDK impl cheats — it uses jdk.internal.misc.JavaLangAccess.newStringUTF8NoRepl (see ZipCoder.UTF8)
  // StandardCharsets.UTF_8.decode doesn't benefit from using direct buffer
  // and introduces char buffer allocation for each decode operation
  val tempNameBytes = ByteArray(4096)
  val endOffset = centralDirPosition + centralDirSize
  while (offset < endOffset) {
    if (buffer.getInt(offset) != 33639248) {
      throw EOFException("Expected central directory size $centralDirSize" +
                         " but only at $offset no valid central directory file header signature")
    }
    val compressedSize = buffer.getInt(offset + 20)
    val uncompressedSize = buffer.getInt(offset + 24)
    val headerOffset = buffer.getInt(offset + 42)
    val method = (buffer.getShort(offset + 10) and 0xffff.toShort()).toByte()
    val nameLengthInBytes = (buffer.getShort(offset + 28) and 0xffff.toShort()).toInt()
    val extraFieldLength = (buffer.getShort(offset + 30) and 0xffff.toShort()).toInt()
    val commentLength = (buffer.getShort(offset + 32) and 0xffff.toShort()).toInt()
    offset += 46
    buffer.position(offset)
    val isDir = buffer.get(offset + nameLengthInBytes - 1) == '/'.code.toByte()
    offset += nameLengthInBytes + extraFieldLength + commentLength

    if (isDir) {
      continue
    }

    buffer.get(tempNameBytes, 0, nameLengthInBytes)
    val name = String(tempNameBytes, 0, nameLengthInBytes, Charsets.UTF_8)
    if (name != INDEX_FILENAME) {
      val zipEntryProcessorResult = entryProcessor(name) {
        getByteBuffer(
          buffer = buffer,
          compressedSize = compressedSize,
          uncompressedSize = uncompressedSize,
          headerOffset = headerOffset,
          nameLengthInBytes = nameLengthInBytes,
          method = method,
          byteBufferAllocator = byteBufferAllocator,
        )
      }

      if (zipEntryProcessorResult == ZipEntryProcessorResult.STOP) {
        return
      }
    }
  }
}

private const val STORED: Byte = 0
private const val DEFLATED: Byte = 8

private fun getByteBuffer(
  buffer: ByteBuffer,
  compressedSize: Int,
  uncompressedSize: Int,
  headerOffset: Int,
  nameLengthInBytes: Int,
  method: Byte,
  byteBufferAllocator: SingleByteBufferAllocator,
): ByteBuffer {
  if (uncompressedSize < 0) {
    throw IOException("no data")
  }

  when (method) {
    STORED -> {
      return computeDataOffsetIfNeededAndReadInputBuffer(
        mappedBuffer = buffer,
        headerOffset = headerOffset,
        nameLengthInBytes = nameLengthInBytes,
        compressedSize = compressedSize,
      )
    }
    DEFLATED -> {
      val inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(
        mappedBuffer = buffer,
        headerOffset = headerOffset,
        nameLengthInBytes = nameLengthInBytes,
        compressedSize = compressedSize,
      )
      val inflater = Inflater(true)
      inflater.setInput(inputBuffer)
      try {
        val result = byteBufferAllocator.allocate(uncompressedSize)
        val oldPosition = result.position()
        while (result.hasRemaining()) {
          val inflatedByteCount = inflater.inflate(result)
          check(inflatedByteCount != 0) {
            "Inflater wants input, but input was already set"
          }
          check(inflatedByteCount == uncompressedSize) {
            "Inflater returned unexpected result: $inflatedByteCount instead of $uncompressedSize"
          }
        }
        result.limit(result.position())
        result.position(oldPosition)
        return result
      }
      catch (e: DataFormatException) {
        throw ZipException(e.message ?: "Invalid ZLIB data format")
      }
      finally {
        inflater.end()
      }
    }
    else -> throw ZipException("Unsupported compression method $method")
  }
}

private fun computeDataOffsetIfNeededAndReadInputBuffer(
  mappedBuffer: ByteBuffer,
  headerOffset: Int,
  nameLengthInBytes: Int,
  compressedSize: Int,
): ByteBuffer {
  val dataOffset = computeDataOffset(mappedBuffer = mappedBuffer, headerOffset = headerOffset, nameLengthInBytes = nameLengthInBytes)
  return mappedBuffer
    .asReadOnlyBuffer()
    .position(dataOffset)
    .limit(dataOffset + compressedSize)
}

private fun computeDataOffset(mappedBuffer: ByteBuffer, headerOffset: Int, nameLengthInBytes: Int): Int {
  val start = headerOffset + 28
  // read actual extra field length
  val extraFieldLength = (mappedBuffer.getShort(start) and 0xffff.toShort()).toInt()
  if (extraFieldLength > 128) {
    // assert just to be sure that we don't read a lot of data in case of some error in a zip file or our impl
    throw UnsupportedOperationException("extraFieldLength expected to be less than 128 bytes but $extraFieldLength")
  }

  return start + 2 + nameLengthInBytes + extraFieldLength
}

private inline fun readZipEntries(buffer: ByteBuffer, fileSize: Int, entryProcessor: EntryProcessor) {
  // MIN_EOCD_SIZE
  var offset = fileSize - 22
  var finished = false

  // first, EOCD
  while (offset >= 0) {
    if (buffer.getInt(offset) == 0x6054B50) {
      finished = true
      break
    }
    offset--
  }
  if (!finished) {
    throw ZipException("Archive is not a ZIP archive")
  }

  var isZip64 = true
  if (offset >= 20 && buffer.getInt(offset - 20) == 0x07064b50) {
    offset = buffer.getLong(offset - (20 - 8)).toInt()
    assert(buffer.getInt(offset) == 0x06064b50)
  }
  else {
    isZip64 = false
  }

  val centralDirSize: Int
  val centralDirPosition: Int
  if (isZip64) {
    centralDirSize = buffer.getLong(offset + 40).toInt()
    centralDirPosition = buffer.getLong(offset + 48).toInt()
  }
  else {
    centralDirSize = buffer.getInt(offset + 12)
    centralDirPosition = buffer.getInt(offset + 16)
  }

  SingleByteBufferAllocator().use { byteBufferAllocator ->
    readCentralDirectory(
      buffer = buffer,
      centralDirPosition = centralDirPosition,
      centralDirSize = centralDirSize,
      entryProcessor = entryProcessor,
      byteBufferAllocator = byteBufferAllocator
    )
  }
  buffer.clear()
}

