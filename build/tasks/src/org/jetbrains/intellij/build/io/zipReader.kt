// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import com.intellij.util.lang.DirectByteBufferPool
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

typealias EntryProcessor = (String, ZipEntry) -> Unit

// only files are processed
fun readZipFile(file: Path, entryProcessor: EntryProcessor) {
  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  mapFileAndUse(file) { buffer, fileSize ->
    readZipEntries(buffer, fileSize, entryProcessor)
  }
}

internal fun mapFileAndUse(file: Path, consumer: (ByteBuffer, fileSize: Int) -> Unit) {
  // FileChannel is strongly required because only FileChannel provides `read(ByteBuffer dst, long position)` method -
  // ability to read data without setting channel position, as setting channel position will require synchronization
  var fileSize: Int
  var mappedBuffer: ByteBuffer
  FileChannel.open(file, EnumSet.of(StandardOpenOption.READ)).use { fileChannel ->
    fileSize = fileChannel.size().toInt()
    mappedBuffer = try {
      fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize.toLong())
    }
    catch (e: UnsupportedOperationException) {
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
      unmapBuffer(mappedBuffer)
    }
  }
}


private fun readCentralDirectory(buffer: ByteBuffer,
                                 centralDirPosition: Int,
                                 centralDirSize: Int,
                                 entryProcessor: EntryProcessor) {
  var offset = centralDirPosition

  // assume that file name is not greater than ~2 KiB
  // JDK impl cheats — it uses jdk.internal.misc.JavaLangAccess.newStringUTF8NoRepl (see ZipCoder.UTF8)
  // StandardCharsets.UTF_8.decode doesn't benefit from using direct buffer and introduces char buffer allocation for each decode
  val tempNameBytes = ByteArray(4096)
  var prevEntry: ZipEntry? = null
  var prevEntryExpectedDataOffset = -1
  val endOffset = centralDirPosition + centralDirSize
  while (offset < endOffset) {
    if (buffer.getInt(offset) != 33639248) {
      throw EOFException("Expected central directory size " + centralDirSize +
                         " but only at " + offset + " no valid central directory file header signature")
    }
    val compressedSize = buffer.getInt(offset + 20)
    val uncompressedSize = buffer.getInt(offset + 24)
    val headerOffset = buffer.getInt(offset + 42)
    val method: Byte = (buffer.getShort(offset + 10) and 0xffff.toShort()).toByte()
    val nameLengthInBytes: Int = (buffer.getShort(offset + 28) and 0xffff.toShort()).toInt()
    val extraFieldLength: Int = (buffer.getShort(offset + 30) and 0xffff.toShort()).toInt()
    val commentLength: Int = (buffer.getShort(offset + 32) and 0xffff.toShort()).toInt()
    if (prevEntry != null && prevEntryExpectedDataOffset == headerOffset - prevEntry.compressedSize) {
      prevEntry.dataOffset = prevEntryExpectedDataOffset
    }
    offset += 46
    buffer.position(offset)
    val isDir = buffer.get(offset + nameLengthInBytes - 1) == '/'.code.toByte()
    offset += nameLengthInBytes + extraFieldLength + commentLength

    if (!isDir) {
      buffer.get(tempNameBytes, 0, nameLengthInBytes)
      val entry = ZipEntry(compressedSize = compressedSize,
                           uncompressedSize = uncompressedSize,
                           headerOffset = headerOffset,
                           nameLengthInBytes = nameLengthInBytes,
                           method = method,
                           buffer = buffer)
      prevEntry = entry
      prevEntryExpectedDataOffset = headerOffset + 30 + nameLengthInBytes + extraFieldLength

      val name = String(tempNameBytes, 0, nameLengthInBytes, Charsets.UTF_8)
      if (name != INDEX_FILENAME) {
        entryProcessor(name, entry)
      }
    }
  }
}

class ZipEntry(@JvmField val compressedSize: Int,
               private val uncompressedSize: Int,
               headerOffset: Int,
               nameLengthInBytes: Int,
               private val method: Byte,
               private val buffer: ByteBuffer) {
  companion object {
    const val STORED: Byte = 0
    const val DEFLATED: Byte = 8
  }

  // headerOffset and nameLengthInBytes
  private val offsets = headerOffset.toLong() shl 32 or (nameLengthInBytes.toLong() and 0xffffffffL)

  @JvmField
  var dataOffset = -1

  val isCompressed: Boolean
    get() = method != STORED

  fun getData(): ByteArray {
    if (uncompressedSize < 0) {
      throw IOException("no data")
    }
    if (buffer.capacity() < dataOffset + compressedSize) {
      throw EOFException()
    }
    when (method) {
      STORED -> {
        val inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(buffer)
        val result = ByteArray(uncompressedSize)
        inputBuffer.get(result)
        return result
      }
      DEFLATED -> {
        run {
          val inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(buffer)
          val inflater = Inflater(true)
          inflater.setInput(inputBuffer)
          var count = uncompressedSize
          val result = ByteArray(count)
          var offset = 0
          try {
            while (count > 0) {
              val n = inflater.inflate(result, offset, count)
              check(n != 0) { "Inflater wants input, but input was already set" }
              offset += n
              count -= n
            }
            return result
          }
          catch (e: DataFormatException) {
            val s = e.message
            throw ZipException(s ?: "Invalid ZLIB data format")
          }
          finally {
            inflater.end()
          }
        }
      }
      else -> throw ZipException("Found unsupported compression method $method")
    }
  }

  fun getByteBuffer(): ByteBuffer {
    if (uncompressedSize < 0) {
      throw IOException("no data")
    }
    if (buffer.capacity() < dataOffset + compressedSize) {
      throw EOFException()
    }

    when (method) {
      STORED -> {
        return computeDataOffsetIfNeededAndReadInputBuffer(buffer)
      }
      DEFLATED -> {
        val inputBuffer = computeDataOffsetIfNeededAndReadInputBuffer(buffer)
        val inflater = Inflater(true)
        inflater.setInput(inputBuffer)
        try {
          val result = DirectByteBufferPool.DEFAULT_POOL.allocate(uncompressedSize)
          while (result.hasRemaining()) {
            check(inflater.inflate(result) != 0) { "Inflater wants input, but input was already set" }
          }
          result.rewind()
          return result
        }
        catch (e: DataFormatException) {
          val s = e.message
          throw ZipException(s ?: "Invalid ZLIB data format")
        }
        finally {
          inflater.end()
        }
      }
      else -> throw ZipException("Found unsupported compression method $method")
    }
  }

  private fun computeDataOffsetIfNeededAndReadInputBuffer(mappedBuffer: ByteBuffer): ByteBuffer {
    var dataOffset = dataOffset
    if (dataOffset == -1) {
      dataOffset = computeDataOffset(mappedBuffer)
    }
    val inputBuffer = mappedBuffer.asReadOnlyBuffer()
    inputBuffer.position(dataOffset)
    inputBuffer.limit(dataOffset + compressedSize)
    return inputBuffer
  }

  private fun computeDataOffset(mappedBuffer: ByteBuffer): Int {
    val headerOffset = (offsets shr 32).toInt()
    val start = headerOffset + 28
    // read actual extra field length
    val extraFieldLength = (mappedBuffer.getShort(start) and 0xffff.toShort()).toInt()
    if (extraFieldLength > 128) {
      // assert just to be sure that we don't read a lot of data in case of some error in zip file or our impl
      throw UnsupportedOperationException("extraFieldLength expected to be less than 128 bytes but $extraFieldLength")
    }

    val nameLengthInBytes = offsets.toInt()
    val result = start + 2 + nameLengthInBytes + extraFieldLength
    dataOffset = result
    return result
  }
}

internal fun readZipEntries(buffer: ByteBuffer, fileSize: Int, entryProcessor: EntryProcessor) {
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
  if (buffer.getInt(offset - 20) == 0x07064b50) {
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
  readCentralDirectory(buffer, centralDirPosition, centralDirSize, entryProcessor)
  buffer.clear()
}

