// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry

internal class ParallelScatterZipCreator(private val executorService: ExecutorService, compress: Boolean) {
  private val streams = ConcurrentLinkedDeque<ScatterZipOutputStream>()
  private val futures = ConcurrentLinkedDeque<Future<ScatterZipOutputStream>>()
  private val startedAt = System.currentTimeMillis()
  private var compressionDoneAt: Long = 0
  private var scatterDoneAt: Long = 0

  /**
   * Returns a message describing the overall statistics of the compression run
   *
   * @return A string
   */
  val statisticsMessage: ScatterStatistics
    get() = ScatterStatistics(compressionDoneAt - startedAt, scatterDoneAt - compressionDoneAt)

  private val tlScatterStreams: ThreadLocal<ScatterZipOutputStream> = ThreadLocal.withInitial {
    val store = MappedByteBufferBasedScatterGatherBackingStore(Files.createTempFile("parallelScatter", ""))
    val scatterStream = ScatterZipOutputStream(store, if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null)
    streams.add(scatterStream)
    scatterStream
  }

  fun addEntry(zipArchiveEntry: ZipEntry, source: Path) {
    futures.add(executorService.submit(Callable {
      val scatterStream = tlScatterStreams.get()
      scatterStream.addArchiveEntry(zipArchiveEntry, source)
      scatterStream
    }))
  }

  fun addEntry(entry: ZipEntry, supplier: () -> InputStream) {
    futures.add(executorService.submit(Callable {
      val scatterStream = tlScatterStreams.get()
      scatterStream.addArchiveEntry(entry, supplier())
      scatterStream
    }))
  }

  fun writeTo(targetStream: ZipArchiveOutputStream) {
    try {
      for (future in futures) {
        future.get()
      }

      compressionDoneAt = System.currentTimeMillis()
      for (future in futures) {
        future.get().zipEntryWriter().writeNextZipEntry(targetStream)
      }
      for (scatterStream in streams) {
        scatterStream.close()
      }
      scatterDoneAt = System.currentTimeMillis()
      streams.clear()
      futures.clear()
    }
    finally {
      closeAll()
    }
  }

  private fun closeAll() {
    for (scatterStream in streams) {
      try {
        scatterStream.close()
      }
      catch (ignore: IOException) {
      }
    }
  }
}

private class ScatterZipOutputStream(private val backingStore: MappedByteBufferBasedScatterGatherBackingStore,
                                      private val deflater: Deflater?) : Closeable {
  private val items = ConcurrentLinkedQueue<CompressedEntry>()
  private val isClosed = AtomicBoolean()
  private var zipEntryWriter: ZipEntryWriter? = null

  private val crc32 = CRC32()

  private var directByteBuffer: ByteBuffer? = null

  private class CompressedEntry(val entry: ZipEntry,
                                val crc: Long,
                                val compressedSize: Int,
                                val size: Int,
                                val endPosition: Int,
                                val nameBytes: ByteArray) {
    fun setSizeAndCompressedSizeAndCrcToArchiveEntry() {
      entry.compressedSize = compressedSize.toLong()
      entry.size = size.toLong()
      entry.crc = crc
    }
  }

  private fun allocateDirectBuffer(size: Int): ByteBuffer {
    var result = directByteBuffer
    if (result == null || result.capacity() < size) {
      result = ByteBuffer.allocateDirect(roundUpInt(size, 65_536))!!
    }
    result.rewind()
    result.limit(size)
    return result
  }

  fun addArchiveEntry(entry: ZipEntry, file: Path) {
    val buffer: ByteBuffer
    Files.newByteChannel(file, EnumSet.of(StandardOpenOption.READ)).use { channel ->
      buffer = allocateDirectBuffer(channel.size().toInt())
      do {
        channel.read(buffer)
      }
      while (buffer.hasRemaining())
      buffer.flip()
    }

    crc32.reset()
    crc32.update(buffer)
    buffer.flip()

    val mappedByteBuffer = backingStore.mappedByteBuffer

    val name = entry.name.toByteArray()
    val compressedSizeOffset = writeLocalFileHeader(mappedByteBuffer, name, buffer.limit(), crc32.value, entry.method)

    val compressedSize: Int
    if (deflater != null && entry.method == ZipEntry.DEFLATED) {
      val oldPosition = mappedByteBuffer.position()
      deflater.setInput(buffer)
      deflater.finish()
      do {
        val n = deflater.deflate(mappedByteBuffer, Deflater.NO_FLUSH)
        assert(n != 0)
      }
      while (buffer.hasRemaining())
      deflater.reset()

      compressedSize = mappedByteBuffer.position() - oldPosition
    }
    else {
      compressedSize = buffer.limit()
      mappedByteBuffer.put(buffer)
    }

    mappedByteBuffer.putInt(compressedSizeOffset, compressedSize)

    items.add(CompressedEntry(entry = entry,
                              crc = crc32.value,
                              compressedSize = compressedSize,
                              size = buffer.limit(),
                              endPosition = mappedByteBuffer.position(),
                              nameBytes = name))
  }

  fun addArchiveEntry(entry: ZipEntry, stream: InputStream) {
    assert(entry.size >= 0)

    val buffer: ByteArray
    stream.use { input ->
      buffer = input.readNBytes(entry.size.toInt())
    }

    crc32.reset()
    crc32.update(buffer)

    val compressedSize: Int
    val mappedByteBuffer = backingStore.mappedByteBuffer

    val name = entry.name.toByteArray()
    val compressedSizeOffset = writeLocalFileHeader(mappedByteBuffer, name, buffer.size, crc32.value, entry.method)

    if (deflater != null && entry.method == ZipEntry.DEFLATED) {
      val oldPosition = mappedByteBuffer.position()
      deflater.setInput(buffer)
      deflater.finish()
      do {
        val n = deflater.deflate(mappedByteBuffer, Deflater.NO_FLUSH)
        assert(n != 0)
      }
      while (!deflater.finished())
      deflater.reset()

      compressedSize = mappedByteBuffer.position() - oldPosition
    }
    else {
      compressedSize = buffer.size
      mappedByteBuffer.put(buffer)
    }

    mappedByteBuffer.putInt(compressedSizeOffset, compressedSize)

    items.add(CompressedEntry(entry = entry,
                              crc = crc32.value,
                              compressedSize = compressedSize,
                              size = buffer.size,
                              endPosition = mappedByteBuffer.position(),
                              nameBytes = name))
  }

  private fun writeLocalFileHeader(buffer: MappedByteBuffer,
                                   name: ByteArray,
                                   size: Int,
                                   crc32: Long,
                                   method: Int): Int {
    buffer.putInt(0x04034b50)
    // Version needed to extract (minimum)
    buffer.putShort(0)
    // General purpose bit flag
    buffer.putShort(0)
    // Compression method
    buffer.putShort(method.toShort())
    // File last modification time
    buffer.putShort(0)
    // File last modification date
    buffer.putShort(0)
    // CRC-32 of uncompressed data
    buffer.putInt((crc32 and 0xffffffffL).toInt())
    val compressedSizeOffset = buffer.position()
    // Compressed size
    buffer.position(compressedSizeOffset + Int.SIZE_BYTES)
    // Uncompressed size
    buffer.putInt(size)
    // File name length
    buffer.putShort((name.size and 0xffff).toShort())
    // Extra field length
    buffer.putShort(0)
    buffer.put(name)
    return compressedSizeOffset
  }

  class ZipEntryWriter(scatter: ScatterZipOutputStream) {
    private val itemIterator = scatter.items.iterator()
    private val buffer = scatter.backingStore.mappedByteBuffer

    init {
      scatter.backingStore.closeForWriting()
      buffer.flip()
    }

    fun writeNextZipEntry(target: ZipArchiveOutputStream) {
      val compressedEntry = itemIterator.next()
      buffer.limit(compressedEntry.endPosition)
      compressedEntry.setSizeAndCompressedSizeAndCrcToArchiveEntry()
      target.addRawArchiveEntry(compressedEntry.entry, buffer, compressedEntry.nameBytes)
    }
  }

  fun zipEntryWriter(): ZipEntryWriter {
    var result = zipEntryWriter
    if (result == null) {
      result = ZipEntryWriter(this)
      zipEntryWriter = result
    }
    return result
  }

  override fun close() {
    if (isClosed.compareAndSet(false, true)) {
      try {
        backingStore.close()
      }
      finally {
        deflater?.end()
      }
    }
  }
}

internal class ScatterStatistics(
  /**
   * The number of milliseconds elapsed in the parallel compression phase
   * @return The number of milliseconds elapsed
   */
  val compressionElapsed: Long,
  /**
   * The number of milliseconds elapsed in merging the results of the parallel compression, the IO phase
   * @return The number of milliseconds elapsed
   */
  val mergingElapsed: Long


)

internal class MappedByteBufferBasedScatterGatherBackingStore(private val target: Path) : Closeable {
  private var closed = false
  val mappedByteBuffer: MappedByteBuffer = (Files.newByteChannel(target, EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE)) as FileChannel)
    .use { fileChannel ->
      // 2GB buffer - should be enough
      val result = fileChannel.map(FileChannel.MapMode.PRIVATE, 0, Integer.MAX_VALUE.toLong())
      result.order(ByteOrder.LITTLE_ENDIAN)
      result
    }

  fun closeForWriting() {
    closed = true
  }

  override fun close() {
    try {
      closeForWriting()
    }
    finally {
      try {
        Files.deleteIfExists(target)
      }
      catch (ignore: AccessDeniedException) {
        @Suppress("SSBasedInspection")
        try {
          target.toFile().deleteOnExit()
        }
        catch (ignore: UnsupportedOperationException) {
          // mem fs
        }
      }
    }
  }
}

private fun roundUpInt(x: Int, @Suppress("SameParameterValue") blockSizePowerOf2: Int): Int {
  return x + blockSizePowerOf2 - 1 and -blockSizePowerOf2
}