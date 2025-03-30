// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.EnumSet

internal val READ = EnumSet.of(StandardOpenOption.READ)

val W_CREATE_NEW: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
val W_OVERWRITE: EnumSet<StandardOpenOption> =
  EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)

val RW: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
val RW_NEW: EnumSet<StandardOpenOption> = EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)

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

fun fileDataWriter(file: Path, options: Set<OpenOption> = RW, useMapped: Boolean = useMappedFileWriter): DataWriter {
  if (useMapped) {
    return MappedFileDataWriter(file, options)
  }
  else {
    return FileChannelDataWriter(FileChannel.open(file, options))
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
    var currentPosition = position
    do {
      currentPosition += fileChannel.write(data, currentPosition)
    }
    while (data.hasRemaining())
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
    val n = to.transferFrom(from, currentPosition, size)
    if (n < 0) {
      throw EOFException("Unexpected end of file while transferring data from position " +
                         "$currentPosition to $newPosition (transferred $n bytes)")
    }
    currentPosition += n
  }
  return newPosition
}