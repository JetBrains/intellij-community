// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.io

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
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

  private class CompressedEntry(val entry: ZipEntry, val crc: Long, val compressedSize: Int, val size: Int) {
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

    val compressedSize: Int
    if (deflater != null && entry.method == ZipEntry.DEFLATED) {
      val oldPosition = backingStore.mappedByteBuffer.position()
      deflater.setInput(buffer)
      deflater.finish()
      do {
        val n = deflater.deflate(backingStore.mappedByteBuffer, Deflater.NO_FLUSH)
        assert(n != 0)
      }
      while (buffer.hasRemaining())
      deflater.reset()

      compressedSize = backingStore.mappedByteBuffer.position() - oldPosition
    }
    else {
      compressedSize = buffer.limit()
      backingStore.mappedByteBuffer.put(buffer)
    }

    items.add(CompressedEntry(entry, crc32.value, compressedSize, buffer.limit()))
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
    if (deflater != null && entry.method == ZipEntry.DEFLATED) {
      val oldPosition = backingStore.mappedByteBuffer.position()
      deflater.setInput(buffer)
      deflater.finish()
      do {
        val n = deflater.deflate(backingStore.mappedByteBuffer, Deflater.NO_FLUSH)
        assert(n != 0)
      }
      while (!deflater.finished())
      deflater.reset()

      compressedSize = backingStore.mappedByteBuffer.position() - oldPosition
    }
    else {
      compressedSize = buffer.size
      backingStore.mappedByteBuffer.put(buffer)
    }

    items.add(CompressedEntry(entry, crc32.value, compressedSize, buffer.size))
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
      buffer.limit((buffer.position() + compressedEntry.compressedSize))
      compressedEntry.setSizeAndCompressedSizeAndCrcToArchiveEntry()
      target.addRawArchiveEntry(compressedEntry.entry, buffer)
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
      fileChannel.map(FileChannel.MapMode.PRIVATE, 0, Integer.MAX_VALUE.toLong())
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