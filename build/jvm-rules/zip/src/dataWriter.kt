// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

val READ_OPEN_OPTION: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.READ)

val W_CREATE_NEW: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)

val WRITE_OPEN_OPTION: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.WRITE)

val W_OVERWRITE: EnumSet<StandardOpenOption> = EnumSet.of(
  StandardOpenOption.WRITE,
  StandardOpenOption.CREATE,
  StandardOpenOption.TRUNCATE_EXISTING,
)

val RW: EnumSet<StandardOpenOption> = EnumSet.of(
  StandardOpenOption.READ,
  StandardOpenOption.WRITE,
  StandardOpenOption.CREATE,
)

interface DataWriter {
  val isNioBufferSupported: Boolean
    get() = false

  fun write(data: ByteBuf, position: Long)

  fun write(data: ByteBuffer, position: Long)

  fun asNioBuffer(requiredSize: Int, position: Long): ByteBuffer? = null

  fun transferFromFileChannel(source: FileChannel, position: Long, size: Int)

  fun close(size: Long)
}

private val useMappedFileWriter = System.getProperty("idea.zip.use.mapped.file.writer", "false").toBoolean()

@PublishedApi
internal fun fileDataWriter(file: Path, overwrite: Boolean, isTemp: Boolean): DataWriter {
  if (useMappedFileWriter) {
    // TRUNCATE_EXISTING option is unnecessary as the MappedFileDataWriter already calls truncate() during its close operation
    return MappedFileDataWriter(file, RW, chunkSize = 128 * 1024)
  }
  else {
    val options = when {
      overwrite -> W_OVERWRITE
      isTemp -> WRITE_OPEN_OPTION
      else -> W_CREATE_NEW
    }
    return FileChannelDataWriter(FileChannel.open(file, options))
  }
}

// test-only
fun testOnlyDataWriter(file: Path, useMapped: Boolean): DataWriter {
  if (useMapped) {
    return MappedFileDataWriter(file, RW)
  }
  else {
    return FileChannelDataWriter(FileChannel.open(file, W_OVERWRITE))
  }
}

class ByteBufferDataWriter(private val buffer: ByteBuf) : DataWriter {
  private val offset = buffer.writerIndex()

  override fun write(data: ByteBuf, position: Long) {
    buffer
      .writerIndex(offset + position.toInt())
      .writeBytes(data)
  }

  override fun write(data: ByteBuffer, position: Long) {
    buffer
      .writerIndex(offset + position.toInt())
      .writeBytes(data)
  }

  override fun transferFromFileChannel(source: FileChannel, position: Long, size: Int) {
    throw UnsupportedOperationException()
  }

  override fun close(size: Long) {
    buffer.writerIndex(offset + size.toInt())
  }

  // test-only
  fun toByteArray(): ByteArray {
    val result = ByteArray(buffer.writerIndex() - offset)
    buffer.getBytes(offset, result)
    return result
  }
}

private class FileChannelDataWriter(
  private val fileChannel: FileChannel,
) : DataWriter {
  override fun write(data: ByteBuf, position: Long) {
    writeToFileChannelFully(channel = fileChannel, position = position, buffer = data)
  }

  override fun write(data: ByteBuffer, position: Long) {
    writeToFileChannelFully(channel = fileChannel, data = data, position = position)
  }

  override fun close(size: Long) {
    fileChannel.close()
  }

  override fun transferFromFileChannel(source: FileChannel, position: Long, size: Int) {
    transferToFully(from = source, size = size.toLong(), to = fileChannel, position = position)
  }
}

private fun transferToFully(from: FileChannel, size: Long, to: FileChannel, position: Long): Long {
  var currentPosition = position
  val newPosition = currentPosition + size
  while (currentPosition < newPosition) {
    val n = to.transferFrom(from, currentPosition, newPosition - currentPosition)
    if (n <= 0) {
      throw EOFException("Unexpected end of file while transferring data from position " +
                         "$currentPosition to $newPosition (transferred ${currentPosition - position} bytes)")
    }
    currentPosition += n
  }
  return newPosition
}