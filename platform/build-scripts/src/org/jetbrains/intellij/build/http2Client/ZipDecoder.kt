// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import com.intellij.util.lang.ImmutableZipFile
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import java.io.EOFException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.math.min

internal val OVERWRITE_OPERATION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

internal sealed interface DataConsumer {
  fun consume(target: ByteBuf)

  fun close()
}

internal class DataToFileConsumer(file: Path) : DataConsumer {
  private var position = 0L
  private val fileChannel = FileChannel.open(file, OVERWRITE_OPERATION)

  override fun consume(target: ByteBuf) {
    var toWrite = target.readableBytes()
    while (toWrite > 0) {
      val n = target.readBytes(fileChannel, position, toWrite)
      if (n < 0) {
        throw EOFException("Unexpected end of file while writing to FileChannel")
      }
      toWrite -= n
      position += n
    }
  }

  override fun close() {
    fileChannel.close()
  }
}

private enum class ZipDecoderState {
  READING_HEADER,
  READING_NAME,
  READING_DATA,
  DONE,
}

internal class ZipDecoder(private val outDir: Path) : DataConsumer {
  private var position = 0L
  private var dataToRead: Int = 0
  private var fileChannel: FileChannel? = null

  private val partialHeader = ByteBufAllocator.DEFAULT.directBuffer(30)

  private var state = ZipDecoderState.READING_HEADER

  // assume that file name is not greater than ~2 KiB
  // StandardCharsets.UTF_8.decode doesn't benefit from using direct buffer and introduces char buffer allocation for each decoding
  private val tempNameBytes = ByteArray(2048)
  private var tempNameBytesPosition = 0
  private var nameLength: Int = 0

  private val createdDirs = HashSet<Path>()

  init {
    Files.createDirectories(outDir)
    createdDirs.add(outDir)
  }

  //private val debugWriter = DataToFileWriter(outDir.resolve("debug.zip"))

  override fun consume(target: ByteBuf) {
    //debugWriter.write(target.slice())

    while (target.isReadable) {
      when (state) {
        ZipDecoderState.READING_HEADER -> {
          if (readHeader(target)) {
            state = ZipDecoderState.READING_NAME
          }
        }
        ZipDecoderState.READING_NAME -> {
          if (readName(target)) {
            state = ZipDecoderState.READING_DATA
          }
        }
        ZipDecoderState.READING_DATA -> {
          if (readData(target)) {
            state = ZipDecoderState.READING_HEADER
            fileChannel!!.close()
            fileChannel = null
            position = 0L
          }
        }
        ZipDecoderState.DONE -> {
          assert(fileChannel == null)
          return
        }
      }
    }
  }

  private fun readHeader(buffer: ByteBuf): Boolean {
    buffer.readBytes(partialHeader, min(partialHeader.writableBytes(), buffer.readableBytes()))

    if (partialHeader.readableBytes() < 30) {
      if (partialHeader.readableBytes() >= 4) {
        val signature = partialHeader.getIntLE(0)
        if (signature == ImmutableZipFile.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
          state = ZipDecoderState.DONE
          partialHeader.clear()
          return false
        }
      }
      // not enough data for the header
      return false
    }

    partialHeader.readerIndex(0)
    val signature = partialHeader.readIntLE()
    if (signature == ImmutableZipFile.CENTRAL_DIRECTORY_FILE_HEADER_SIGNATURE) {
      // encountered central directory file header signature - signal to stop processing as we have reached the end of the ZIP entries
      state = ZipDecoderState.DONE
      partialHeader.clear()
      return false
    }
    else if (signature != 0x04034b50) {
      throw IllegalStateException("Invalid ZIP entry signature")
    }

    // skip unnecessary bytes
    partialHeader.readerIndex(22)
    dataToRead = partialHeader.readIntLE()
    nameLength = partialHeader.readUnsignedShortLE()
    // extraLength
    assert(partialHeader.readUnsignedShortLE() == 0)

    require(nameLength > 0) { "Name cannot empty for ZIP entry" }
    partialHeader.clear()
    return true
  }

  private fun readName(buffer: ByteBuf): Boolean {
    val available = buffer.readableBytes()
    val nameToRead = nameLength - tempNameBytesPosition
    if (available < nameToRead) {
      buffer.readBytes(tempNameBytes, tempNameBytesPosition, available)
      tempNameBytesPosition += available
      return false
    }

    buffer.readBytes(tempNameBytes, tempNameBytesPosition, nameToRead)
    tempNameBytesPosition = 0

    val isDir = tempNameBytes[nameLength - 1] == '/'.code.toByte()
    if (isDir) {
      return false
    }

    assert(position == 0L)
    assert(fileChannel == null)

    val file = outDir.resolve(String(tempNameBytes, 0, nameLength))
    val parent = file.parent
    if (createdDirs.add(parent)) {
      Files.createDirectories(parent)
    }
    fileChannel = FileChannel.open(file, OVERWRITE_OPERATION)
    return true
  }

  private fun readData(buffer: ByteBuf): Boolean {
    var toWrite = buffer.readableBytes().coerceAtMost(min(dataToRead, 4 * 1024 * 1024))
    if (toWrite > 0) {
      dataToRead -= toWrite
      val fileChannel = fileChannel!!
      do {
        val n = buffer.readBytes(fileChannel, position, toWrite)
        if (n < 0) {
          throw EOFException("Unexpected end of file while writing to FileChannel")
        }
        toWrite -= n
        position += n
      }
      while (toWrite > 0)
    }

    return dataToRead == 0
  }

  override fun close() {
    try {
      fileChannel?.close()
    }
    finally {
      partialHeader.release()
    }
  }
}