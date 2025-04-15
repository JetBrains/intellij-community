// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream.Companion.FLUSH_THRESHOLD
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream.Companion.INITIAL_BUFFER_CAPACITY
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry

fun zipWriter(
  targetFile: Path,
  packageIndexBuilder: PackageIndexBuilder?,
  overwrite: Boolean = false,
): ZipArchiveOutputStream {
  return ZipArchiveOutputStream(
    dataWriter = fileDataWriter(file = targetFile, overwrite = overwrite, isTemp = false),
    zipIndexWriter = ZipIndexWriter(packageIndexBuilder),
  )
}

class ZipArchiveOutputStream(
  private val dataWriter: DataWriter,
  private val zipIndexWriter: ZipIndexWriter,
) : AutoCloseable {
  companion object {
    private val emptyByteArray = ByteArray(0)

    // visible for tests
    /**
     * Defines the threshold for flushing the buffer to disk (1MB).
     * When the amount of data in the buffer exceeds this threshold, the buffer is flushed to disk.
     */
    const val FLUSH_THRESHOLD: Int = 1 * 1024 * 1024

    /**
     * Defines the initial capacity of the buffer (128KB).
     *
     * We use two constants ([FLUSH_THRESHOLD] and [INITIAL_BUFFER_CAPACITY]) to optimize memory usage and write performance independently.
     * [INITIAL_BUFFER_CAPACITY] controls the initial memory footprint.
     * A smaller value reduces memory consumption for small ZIP archives.
     * [FLUSH_THRESHOLD] controls *when* the buffer is flushed to disk.
     * It allows us to accumulate a larger chunk of data before writing, improving I/O efficiency,
     * *without* requiring us to allocate a large buffer upfront for every archive, even small ones.
     * We can flush the smaller buffer more frequently if the [FLUSH_THRESHOLD] is reached, preventing excessive memory usage.
     */
    const val INITIAL_BUFFER_CAPACITY: Int = 128 * 1024
    
    init {
      // https://github.com/netty/netty/issues/11532
      if (System.getProperty("io.netty.tryReflectionSetAccessible") == null) {
        System.setProperty("io.netty.tryReflectionSetAccessible", "true")
      }
    }
  }

  private var finished = false
  private val buffer = byteBufferAllocator.directBuffer(INITIAL_BUFFER_CAPACITY)
  private var channelPosition = 0L

  @Suppress("DuplicatedCode")
  @Synchronized
  internal fun addDirEntries(names: Collection<String>) {
    if (dataWriter.isNioBufferSupported) {
      channelPosition = writeDirEntriesUsingNioBuffer(names, flushBufferIfNeeded(0), dataWriter, zipIndexWriter)
    }
    else {
      writeDirEntries(names, flushBufferIfNeeded(), zipIndexWriter, buffer)
    }
  }

  @Suppress("DuplicatedCode")
  private fun addDirEntries(names: Array<String>) {
    if (dataWriter.isNioBufferSupported) {
      channelPosition = writeDirEntriesUsingNioBuffer(names, flushBufferIfNeeded(0), dataWriter, zipIndexWriter)
    }
    else {
      writeDirEntries(names = names, channelPosition = flushBufferIfNeeded(), zipIndexWriter = zipIndexWriter, buffer = buffer)
    }
  }

  @Synchronized
  fun writeDataWithUnknownSize(path: ByteArray, estimatedSize: Int, crc32: CRC32?, task: (ByteBuf) -> Unit) {
    val headerOffset = flushBufferIfNeeded()
    val headerSize = 30 + path.size
    val headerPosition = buffer.writerIndex()
    val endOfHeaderPosition = headerPosition + headerSize
    buffer.ensureWritable(headerSize + estimatedSize.coerceAtLeast(1024))
    buffer.writerIndex(endOfHeaderPosition)
    task(buffer)
    val size = buffer.writerIndex() - endOfHeaderPosition
    val crc = if (crc32 == null || size == 0) {
      0
    }
    else {
      crc32.compute(buffer.internalNioBuffer(endOfHeaderPosition, size))
    }
    buffer.writerIndex(headerPosition)
    writeZipLocalFileHeader(path = path, size = size, crc32 = crc, buffer = buffer)
    buffer.writerIndex(endOfHeaderPosition + size)
    zipIndexWriter.writeCentralFileHeader(
      path = path,
      size = size,
      compressedSize = size,
      method = ZipEntry.STORED,
      crc = crc,
      headerOffset = headerOffset,
      dataOffset = headerOffset + headerSize,
    )
  }

  internal data class CompressedSizeAndCrc(@JvmField val compressedSize: Int, @JvmField val crc: Long)

  @Synchronized
  internal fun writeMaybeCompressed(path: ByteArray, dataSize: Int, task: (resultConsumer: (ByteBuffer) -> Unit) -> CompressedSizeAndCrc) {
    val headerSize = 30 + path.size
    val localFileHeaderOffset = flushBufferIfNeeded(0)
    channelPosition += headerSize
    var (compressedSize, crc) = task(::writeBuffer)
    val method = if (compressedSize == -1) {
      compressedSize = dataSize
      ZipEntry.STORED
    }
    else {
      ZipEntry.DEFLATED
    }

    val endPosition = channelPosition
    val compressedSizeByPosition = endPosition - localFileHeaderOffset - headerSize
    require(compressedSizeByPosition.toInt() == compressedSize) {
      "Expected $compressedSize, actual $compressedSizeByPosition"
    }
    writeZipLocalFileHeader(path = path, size = dataSize, compressedSize = compressedSize, crc32 = crc, method = method, buffer = buffer)
    require(buffer.readableBytes() == headerSize)
    dataWriter.write(buffer, localFileHeaderOffset)
    buffer.clear()
    channelPosition = endPosition

    zipIndexWriter.writeCentralFileHeader(
      path = path,
      size = dataSize,
      compressedSize = compressedSize,
      method = method,
      crc = crc,
      headerOffset = localFileHeaderOffset,
      dataOffset = localFileHeaderOffset + headerSize,
    )
  }

  @Suppress("unused")
  @Synchronized
  fun writeUndeclaredData(maxSize: Int, task: (ByteBuffer, Long) -> Int) {
    writeUsingNioBufferAndAllocateSeparateIfLargeData(maxSize, task)
  }

  // returns start position
  @Suppress("unused")
  @Synchronized
  fun writeUndeclaredDataWithKnownSize(data: ByteBuffer): Long {
    val size = data.remaining()
    if (dataWriter.isNioBufferSupported) {
      val position = flushBufferIfNeeded(0)
      val size = size
      dataWriter.write(data, position)
      channelPosition = position + size
      return position
    }
    else {
      return writeData(data, size)
    }
  }

  private fun writeData(data: ByteBuffer, size: Int): Long {
    val writableBytes = buffer.writableBytes()
    val position = channelPosition + buffer.readableBytes()
    if (writableBytes >= size) {
      buffer.writeBytes(data)
    }
    else {
      // write partial data to buffer, flush it, then handle remaining data
      if (writableBytes > 0) {
        val limit = data.limit()
        data.limit(data.position() + writableBytes)
        buffer.writeBytes(data)
        data.limit(limit)
      }

      writeBuffer(buffer)
      buffer.clear()

      val rest = size - writableBytes
      // directly write large data to avoid unnecessary buffer resizing
      if (rest >= buffer.writableBytes()) {
        writeBuffer(data)
      }
      else {
        buffer.writeBytes(data)
      }
    }
    return position
  }

  @Synchronized
  private fun flushBufferIfNeeded(threshold: Int = FLUSH_THRESHOLD): Long {
    val readableBytes = buffer.readableBytes()
    if (readableBytes > threshold) {
      writeBuffer(buffer)
      buffer.clear()
      return channelPosition
    }
    else {
      return channelPosition + readableBytes
    }
  }

  @Synchronized
  internal fun transferFromFileChannel(path: ByteArray, source: FileChannel, size: Int, crc32: CRC32?) {
    if (size == 0) {
      uncompressedData(path = path, data = emptyByteArray, crc32 = crc32)
      return
    }

    if (crc32 == null) {
      zipIndexWriter.writeCentralFileHeader(path = path, size = size, crc = 0, headerOffset = flushBufferIfNeeded())

      writeZipLocalFileHeader(path = path, size = size, crc32 = 0, buffer = buffer)
      val position = flushBufferIfNeeded(0)
      dataWriter.transferFromFileChannel(source, position, size)
      channelPosition = position + size
    }
    else if (dataWriter.isNioBufferSupported) {
      // before dataWriter.asBuffer
      val headerOffset = flushBufferIfNeeded(0)
      val headerSize = 30 + path.size
      val headerAndDataSize = headerSize + size
      val buffer = dataWriter.asNioBuffer(headerAndDataSize, headerOffset)!!

      val headerPosition = buffer.position()
      val endOfHeaderPosition = headerPosition + headerSize
      buffer.position(endOfHeaderPosition)
      copyFromFileChannelToBuffer(sourceChannel = source, buffer = buffer, size = size.toLong(), file = null)

      buffer.position(endOfHeaderPosition)
      assert(buffer.limit() == headerPosition + headerAndDataSize)
      crc32.reset()
      crc32.update(buffer)
      val crc = crc32.value
      buffer.position(headerPosition)
      writeZipLocalFileHeader(path = path, size = size, crc32 = crc, buffer = buffer)

      zipIndexWriter.writeCentralFileHeader(path = path, size = size, crc = crc, headerOffset = headerOffset)
      channelPosition += headerAndDataSize
    }
    else {
      // we have to compute CRC32 for a file, so, we cannot use `FileChannel.transferTo`
      val fileData = source.map(FileChannel.MapMode.READ_ONLY, 0, size.toLong())
      try {
        uncompressedData(path = path, data = fileData, crc32 = crc32)
      }
      finally {
        unmapBuffer(fileData)
      }
    }
  }

  @Synchronized
  fun fileWithoutCrc(path: ByteArray, file: Path) {
    FileChannel.open(file, READ_OPEN_OPTION).use { sourceChannel ->
      val size = sourceChannel.size()
      if (size > Int.MAX_VALUE) {
        throw IOException("File sizes over 2 GB are not supported: $size")
      }

      transferFromFileChannel(path = path, source = sourceChannel, size = size.toInt(), crc32 = null)
    }
  }

  @Synchronized
  fun writeDataWithKnownSize(path: ByteArray, size: Int, crc32: CRC32? = null, task: (ByteBuffer) -> Unit) {
    val headerAndDataSize = 30 + path.size + size
    writeUsingNioBufferAndAllocateSeparateIfLargeData(headerAndDataSize) { nioBuffer, localFileHeaderOffset ->
      writeNioBuffer(
        path = path,
        crc32 = crc32,
        localFileHeaderOffset = localFileHeaderOffset,
        buffer = nioBuffer,
        zipIndexWriter = zipIndexWriter,
        task = task,
      )
      headerAndDataSize
    }
  }

  private fun writeUsingNioBufferAndAllocateSeparateIfLargeData(headerAndDataSize: Int, task: (ByteBuffer, Long) -> Int) {
    if (dataWriter.isNioBufferSupported) {
      val localFileHeaderOffset = flushBufferIfNeeded(0)
      val nioBuffer = dataWriter.asNioBuffer(headerAndDataSize, localFileHeaderOffset)!!
      val size = task(nioBuffer, localFileHeaderOffset)
      channelPosition += size
      return
    }

    var buffer = buffer
    var releaseBuffer = false
    val localFileHeaderOffset = if (buffer.writableBytes() < headerAndDataSize) {
      if (buffer.isReadable) {
        writeBuffer(buffer)
        buffer.clear()
      }

      if (headerAndDataSize > INITIAL_BUFFER_CAPACITY) {
        // instead of resizing the current buffer, it's preferable to obtain a buffer of the required size from a pool
        buffer = byteBufferAllocator.directBuffer(headerAndDataSize)
        releaseBuffer = true
      }

      channelPosition
    }
    else {
      channelPosition + buffer.readableBytes()
    }

    try {
      val nioBuffer = buffer.internalNioBuffer(buffer.writerIndex(), headerAndDataSize)
      val oldOrder = nioBuffer.order()
      nioBuffer.order(ByteOrder.LITTLE_ENDIAN)
      val size = task(nioBuffer, localFileHeaderOffset)
      nioBuffer.order(oldOrder)
      buffer.writerIndex(buffer.writerIndex() + size)
      if (releaseBuffer) {
        writeBuffer(buffer)
      }
    }
    finally {
      if (releaseBuffer) {
        buffer.release()
      }
    }
  }

  @Suppress("DuplicatedCode")
  @Synchronized
  fun uncompressedData(path: ByteArray, data: ByteArray, crc32: CRC32?) {
    val size = data.size
    val crc = crc32?.compute(data) ?: 0
    if (dataWriter.isNioBufferSupported) {
      putUncompressedDataToMappedBuffer(path = path, size = size, crc = crc) {
        it.put(data)
      }
    }
    else {
      val headerOffset = flushBufferIfNeeded()
      writeZipLocalFileHeader(path = path, size = size, crc32 = crc, buffer = buffer)
      zipIndexWriter.writeCentralFileHeader(path = path, size = size, crc = crc, headerOffset = headerOffset)

      val writableBytes = buffer.writableBytes()
      if (writableBytes >= size) {
        buffer.writeBytes(data)
      }
      else {
        // write partial data to buffer, flush it, then handle remaining data
        val sourceOffset = writableBytes
        if (writableBytes > 0) {
          buffer.writeBytes(data, 0, writableBytes)
          writeBuffer(buffer)
          buffer.clear()
        }

        val rest = size - sourceOffset
        // directly write large data to avoid unnecessary buffer resizing
        if (rest >= buffer.writableBytes()) {
          writeBuffer(ByteBuffer.wrap(data, sourceOffset, rest))
        }
        else {
          buffer.writeBytes(data, sourceOffset, rest)
        }
      }
    }
  }

  @Suppress("DuplicatedCode")
  @Synchronized
  fun uncompressedData(path: ByteArray, data: ByteBuffer, crc32: CRC32?) {
    val size = data.remaining()
    val crc = crc32?.compute(data) ?: 0
    if (dataWriter.isNioBufferSupported) {
      putUncompressedDataToMappedBuffer(path = path, size = size, crc = crc) {
        it.put(data)
      }
    }
    else {
      val headerOffset = flushBufferIfNeeded()
      writeZipLocalFileHeader(path = path, size = size, crc32 = crc, buffer = buffer)
      zipIndexWriter.writeCentralFileHeader(path = path, size = size, crc = crc, headerOffset = headerOffset)
      writeData(data, size)
    }
  }

  private inline fun putUncompressedDataToMappedBuffer(path: ByteArray, size: Int, crc: Long, task: (ByteBuffer) -> Unit) {
    // before dataWriter.asBuffer
    val headerOffset = flushBufferIfNeeded(0)
    val headerAndDataSize = 30 + path.size + size
    val buffer = dataWriter.asNioBuffer(headerAndDataSize, headerOffset)!!
    writeZipLocalFileHeader(path = path, size = size, crc32 = crc, buffer = buffer)
    task(buffer)
    channelPosition += headerAndDataSize
    zipIndexWriter.writeCentralFileHeader(path = path, size = size, crc = crc, headerOffset = headerOffset)
  }

  private fun writeBuffer(data: ByteBuf) {
    val size = data.readableBytes()
    assert(size != 0)
    dataWriter.write(data, channelPosition)
    channelPosition += size
  }

  private fun writeBuffer(data: ByteBuffer) {
    assert(!buffer.isReadable)

    val size = data.remaining()
    dataWriter.write(data, channelPosition)
    channelPosition += size
  }

  @Synchronized
  override fun close() {
    if (finished) {
      return
    }

    try {
      try {
        val packageIndexBuilder = zipIndexWriter.packageIndexBuilder
        val indexDataEnd = if (packageIndexBuilder == null || zipIndexWriter.isEmpty()) {
          -1
        }
        else {
          packageIndexBuilder.writePackageIndex {
            addDirEntries(it)
          }

          val indexWriter = packageIndexBuilder.indexWriter
          // ditto on macOS doesn't like arbitrary data in zip file - wrap into zip entry
          val indexDataSize = indexWriter.dataSize()
          val indexDataEnd = flushBufferIfNeeded() + indexDataSize + 30 + INDEX_FILENAME_BYTES.size
          writeIndex(indexWriter, indexDataSize, this)
          indexDataEnd.toInt()
        }

        val unwrittenDataSize = buffer.readableBytes()

        // write central directory file header
        val zipIndexData = zipIndexWriter.finish(centralDirectoryOffset = channelPosition + unwrittenDataSize, indexDataEnd = indexDataEnd)
        if (unwrittenDataSize != 0 && buffer.writableBytes() >= zipIndexData.readableBytes()) {
          buffer.writeBytes(zipIndexData)
          writeBuffer(buffer)
        }
        else {
          if (unwrittenDataSize != 0) {
            writeBuffer(buffer)
          }
          writeBuffer(zipIndexData)
        }

        finished = true
      }
      finally {
        dataWriter.close(channelPosition)
      }
    }
    finally {
      try {
        zipIndexWriter.release()
      }
      finally {
        buffer.release()
      }
    }
  }
}