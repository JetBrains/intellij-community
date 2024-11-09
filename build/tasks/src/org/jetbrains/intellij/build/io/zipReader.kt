// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import com.intellij.util.lang.ImmutableZipFile
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

typealias EntryProcessor = (String, () -> ByteBuffer) -> Unit

// only files are processed
fun readZipFile(file: Path, entryProcessor: EntryProcessor) {
  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  mapFileAndUse(file) { buffer, fileSize ->
    readZipEntries(buffer = buffer, fileSize = fileSize, entryProcessor = entryProcessor)
  }
}

suspend fun suspendAwareReadZipFile(file: Path, entryProcessor: suspend (String, () -> ByteBuffer) -> Unit) {
  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  mapFileAndUse(file) { buffer, fileSize ->
    readZipEntries(buffer = buffer, fileSize = fileSize, entryProcessor = { it, name ->
      entryProcessor(it, name)
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
  catch (e: IOException) {
    throw IOException(file.toString(), e)
  }
  finally {
    if (mappedBuffer.isDirect) {
      // on Windows memory-mapped file cannot be deleted without clearing in-memory buffer first
      unmapBuffer(mappedBuffer)
    }
  }
}

internal inline fun readCentralDirectory(buffer: ByteBuffer, centralDirPosition: Int, centralDirSize: Int, entryProcessor: EntryProcessor) {
  var offset = centralDirPosition

  // assume that file name is not greater than ~2 KiB
  // JDK impl cheats — it uses jdk.internal.misc.JavaLangAccess.newStringUTF8NoRepl (see ZipCoder.UTF8)
  // StandardCharsets.UTF_8.decode doesn't benefit from using direct buffer
  // and introduces char buffer allocation for each decode operation
  val tempNameBytes = ByteArray(4096)
  val endOffset = centralDirPosition + centralDirSize
  val byteBufferAllocator = ByteBufferAllocator()
  byteBufferAllocator.use {
    while (offset < endOffset) {
      if (buffer.getInt(offset) != 33639248) {
        throw EOFException("Expected central directory size " + centralDirSize +
                           " but only at " + offset + " no valid central directory file header signature")
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
        entryProcessor(name) {
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
      }
    }
  }
}

private const val STORED: Byte = 0
private const val DEFLATED: Byte = 8

internal fun getByteBuffer(
  buffer: ByteBuffer,
  compressedSize: Int,
  uncompressedSize: Int,
  headerOffset: Int,
  nameLengthInBytes: Int,
  method: Byte,
  byteBufferAllocator: ByteBufferAllocator,
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
        while (result.hasRemaining()) {
          check(inflater.inflate(result) != 0) { "Inflater wants input, but input was already set" }
        }
        result.rewind()
        return result
      }
      catch (e: DataFormatException) {
        throw ZipException(e.message ?: "Invalid ZLIB data format")
      }
      finally {
        inflater.end()
      }
    }
    else -> throw ZipException("Found unsupported compression method $method")
  }
}

private fun computeDataOffsetIfNeededAndReadInputBuffer(mappedBuffer: ByteBuffer,
                                                        headerOffset: Int,
                                                        nameLengthInBytes: Int,
                                                        compressedSize: Int): ByteBuffer {
  val dataOffset = computeDataOffset(mappedBuffer = mappedBuffer, headerOffset = headerOffset, nameLengthInBytes = nameLengthInBytes)
  val inputBuffer = mappedBuffer.asReadOnlyBuffer()
  inputBuffer.position(dataOffset)
  inputBuffer.limit(dataOffset + compressedSize)
  return inputBuffer
}

private fun computeDataOffset(mappedBuffer: ByteBuffer, headerOffset: Int, nameLengthInBytes: Int): Int {
  val start = headerOffset + 28
  // read actual extra field length
  val extraFieldLength = (mappedBuffer.getShort(start) and 0xffff.toShort()).toInt()
  if (extraFieldLength > 128) {
    // assert just to be sure that we don't read a lot of data in case of some error in zip file or our impl
    throw UnsupportedOperationException("extraFieldLength expected to be less than 128 bytes but $extraFieldLength")
  }

  return start + 2 + nameLengthInBytes + extraFieldLength
}

private inline fun readZipEntries(buffer: ByteBuffer, fileSize: Int, entryProcessor: EntryProcessor) {
  var offset = fileSize - ImmutableZipFile.MIN_EOCD_SIZE
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
  readCentralDirectory(buffer = buffer,
                       centralDirPosition = centralDirPosition,
                       centralDirSize = centralDirSize,
                       entryProcessor = entryProcessor)
  buffer.clear()
}

