// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Path

private const val MAX_CHUNK_SIZE = Int.MAX_VALUE.toLong()

/**
 * For each operation, we explicitly set the limit to ensure the provided size is correct
 * and to avoid writing beyond the logical block boundary.
 * This also applies not only to public [asNioBuffer], but also for the private method [ensureMappedAndSetPositionAndLimit].
 */
class MappedFileDataWriter(
  file: Path,
  options: Set<OpenOption>,
  private var chunkSize: Int = 128 * 1024,
) : DataWriter {
  private var mappedBuffer: MappedByteBuffer
  // offset of the currently mapped chunk
  private var fileOffset = 0L

  private val fileChannel = FileChannel.open(file, options)

  init {
    mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, chunkSize.toLong())
    mappedBuffer.order(ByteOrder.LITTLE_ENDIAN)
    mappedBuffer.limit(0)
  }

  override val isNioBufferSupported: Boolean
    get() = true

  @Synchronized
  override fun asNioBuffer(requiredSize: Int, position: Long): ByteBuffer {
    return ensureMappedAndSetPositionAndLimit(position, requiredSize.toLong())
  }

  private fun ensureMappedAndSetPositionAndLimit(position: Long, requiredDataSize: Long): MappedByteBuffer {
    require(position >= 0) { "Position must be positive" }
    require(requiredDataSize >= 0) { "Required size must be positive" }

    val relativePosition = toRelativePosition(position)
    if ((chunkSize - relativePosition) >= requiredDataSize) {
      return mappedBuffer
        .limit(relativePosition + requiredDataSize.toInt())
        .position(relativePosition)
    }

    require(requiredDataSize <= MAX_CHUNK_SIZE) { "Chunk size cannot exceed 2GB" }

    // release existing buffer before remapping
    if (mappedBuffer.limit() != 0) {
      mappedBuffer.force(0, mappedBuffer.limit())
    }
    unmapBuffer(mappedBuffer)

    chunkSize = maxOf(roundUpInt(requiredDataSize.toInt(), 65_536), chunkSize * 2, chunkSize)

    fileOffset = position
    mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, position, chunkSize.toLong())
    mappedBuffer.order(ByteOrder.LITTLE_ENDIAN)
    return mappedBuffer.limit(requiredDataSize.toInt())
  }

  @Synchronized
  override fun write(data: ByteBuf, position: Long) {
    val size = data.readableBytes()
    val nioBuffer = data.internalNioBuffer(data.readerIndex(), size)
    ensureMappedAndSetPositionAndLimit(position, size.toLong())
      .put(toRelativePosition(position), nioBuffer, nioBuffer.position(), size)
  }

  @Synchronized
  override fun write(data: ByteBuffer, position: Long) {
    val size = data.remaining()
    ensureMappedAndSetPositionAndLimit(position, size.toLong())
      .put(toRelativePosition(position), data, data.position(), size)
  }

  private fun toRelativePosition(position: Long): Int {
    val result = (position - fileOffset).toInt()
    require(result >= 0) {
      "Position $position is outside of the mapped file"
    }
    return result
  }

  override fun transferFromFileChannel(source: FileChannel, position: Long, size: Int) {
    copyFromFileChannelToBuffer(
      sourceChannel = source,
      buffer = ensureMappedAndSetPositionAndLimit(position, size.toLong()),
      size = size.toLong(),
      file = null,
    )
  }

  @Synchronized
  override fun close(size: Long) {
    try {
      val length = toRelativePosition(size)
      if (length > 0) {
        mappedBuffer.force(0, length)
      }
      unmapBuffer(mappedBuffer)
    }
    finally {
      try {
        fileChannel.truncate(size)
      }
      finally {
        fileChannel.close()
      }
    }
  }
}