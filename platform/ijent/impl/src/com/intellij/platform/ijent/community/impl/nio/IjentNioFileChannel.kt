// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.platform.eel.fs.EelPosixFileInfo
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.*
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class IjentNioFileChannel private constructor(
  private val ijentOpenedFile: EelOpenedFile,
  // we keep stacktrace of the cause of closing for troubleshooting
  @Volatile
  private var closeOrigin: Throwable? = null,
) : FileChannel() {
  companion object {
    @JvmStatic
    internal suspend fun createReading(nioFs: IjentNioFileSystem, path: EelPath.Absolute): IjentNioFileChannel =
      IjentNioFileChannel(nioFs.ijentFs.openForReading(path).getOrThrowFileSystemException())

    @JvmStatic
    internal suspend fun createWriting(
      nioFs: IjentNioFileSystem,
      options: EelFileSystemApi.WriteOptions,
    ): IjentNioFileChannel =
      IjentNioFileChannel(nioFs.ijentFs.openForWriting(options).getOrThrowFileSystemException())

    @JvmStatic
    internal suspend fun createReadingWriting(
      nioFs: IjentNioFileSystem,
      options: EelFileSystemApi.WriteOptions,
    ): IjentNioFileChannel {
      return IjentNioFileChannel(nioFs.ijentFs.openForReadingAndWriting(options).getOrThrowFileSystemException())
    }
  }

  override fun toString(): String = "IjentNioFileChannel($ijentOpenedFile)"

  override fun read(dst: ByteBuffer): Int {
    return readFromPosition(dst, null)
  }

  override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    checkClosed()
    when (ijentOpenedFile) {
      is EelOpenedFile.Reader -> Unit
      is EelOpenedFile.Writer -> throw NonReadableChannelException()
    }

    var totalRead = 0L
    fsBlocking {
      handleThatSmartMultiBufferApi(dsts, offset, length) { buf ->
        val read = when (val res = ijentOpenedFile.read(buf).getOrThrowFileSystemException()) {
          is EelOpenedFile.Reader.ReadResult.Bytes -> res.bytesRead
          is EelOpenedFile.Reader.ReadResult.EOF -> return@fsBlocking
        }
        totalRead += read
      }
    }
    return totalRead
  }

  override fun write(src: ByteBuffer): Int {
    return writeToPosition(src, null)
  }

  override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    checkClosed()
    when (ijentOpenedFile) {
      is EelOpenedFile.Writer -> Unit
      is EelOpenedFile.Reader -> throw NonWritableChannelException()
    }

    var totalWritten = 0L
    fsBlocking {
      handleThatSmartMultiBufferApi(srcs, offset, length) { buf ->
        val written = ijentOpenedFile.write(buf).getOrThrowFileSystemException()
        if (written <= 0) {  // A non-strict comparison.
          return@fsBlocking
        }
        else {
          totalWritten += written
        }
      }
    }
    return totalWritten
  }

  private inline fun handleThatSmartMultiBufferApi(
    buffers: Array<out ByteBuffer>,
    offset: Int,
    length: Int,
    body: (ByteBuffer) -> Unit,
  ) {
    if (buffers.isEmpty()) throw IndexOutOfBoundsException("Empty buffer")
    if (offset !in 0..<buffers.size) throw IndexOutOfBoundsException("Attempting to write to a buffer at $offset which is out of range [0..${buffers.size})")
    if (length < 0) throw IndexOutOfBoundsException("Number of written buffers $length is negative")
    if (length > buffers.size - offset) throw IndexOutOfBoundsException("Attempting to write to $length buffers while only ${buffers.size - offset} are available")

    val iter = buffers.asSequence().take(length).iterator()
    if (iter.hasNext()) {
      var buf = iter.next()
      buf.position(buf.position() + offset)
      while (true) {
        body(buf)  // Can return through the whole function.
        buf = when {
          buf.hasRemaining() -> buf
          iter.hasNext() -> iter.next()
          else -> break
        }
      }
    }
  }

  override fun position(): Long {
    checkClosed()
    return fsBlocking {
      ijentOpenedFile.tell().getOrThrowFileSystemException()
    }
  }

  override fun position(newPosition: Long): FileChannel {
    checkClosed()
    return fsBlocking {
      ijentOpenedFile.seek(newPosition, EelOpenedFile.SeekWhence.START).getOrThrowFileSystemException()
      this@IjentNioFileChannel
    }
  }

  override fun size(): Long {
    checkClosed()
    return fsBlocking {
      return@fsBlocking when (val type = ijentOpenedFile.stat().getOrThrowFileSystemException().type) {
        is EelFileInfo.Type.Regular -> type.size
        is EelFileInfo.Type.Directory, is EelFileInfo.Type.Other -> throw IOException("This file channel is opened for a directory")
        is EelPosixFileInfo.Type.Symlink -> throw IllegalStateException("Internal error: symlink should be resolved for a file channel")
      }
    }
  }

  override fun truncate(size: Long): FileChannel = apply {
    checkClosed()
    val file = when (ijentOpenedFile) {
      is EelOpenedFile.Writer -> ijentOpenedFile
      is EelOpenedFile.Reader -> throw NonWritableChannelException()
    }
    val currentSize = this.size()
    fsBlocking {
      if (size < currentSize) {
        file.truncate(size).getOrThrowFileSystemException()
      }
      val currentPosition = file.tell().getOrThrowFileSystemException()
      file.seek(currentPosition.coerceIn(0, size), EelOpenedFile.SeekWhence.START)
    }
    return this
  }

  override fun force(metaData: Boolean) {
    checkClosed()
    TODO("Not yet implemented -> com.intellij.platform.ijent.functional.fs.TodoOperation.FILE_FORCE")
  }

  // todo the following two methods can recognize that they are working on the same IJent instance,
  // and therefore perform byte copying entirely on the remote side
  override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long {
    checkClosed()
    if (position < 0) {
      throw IllegalArgumentException("Position $position is negative")
    }
    val buf = ByteBuffer.allocate(count.toInt())
    var currentPosition = position
    var totalBytesWritten = 0
    do {
      val bytesRead = read(buf, currentPosition)
      if (bytesRead <= 0) {
        break
      }
      currentPosition += bytesRead
      buf.flip()
      val bytesWritten = target.write(buf)
      if (bytesWritten <= 0) {
        break
      }
      totalBytesWritten += bytesWritten
    }
    while (true)
    return totalBytesWritten.toLong()
  }

  override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long {
    checkClosed()
    if (position < 0) {
      throw IllegalArgumentException("Position $position is negative")
    }
    val buf = ByteBuffer.allocate(count.toInt())
    var currentPosition = 0
    var totalBytesRead = 0
    do {
      val bytesRead = src.read(buf)
      if (bytesRead == 0) {
        break
      }
      totalBytesRead += bytesRead
      buf.flip()
      val bytesWritten = write(buf, position)
      if (bytesWritten == 0) {
        break
      }
      currentPosition += bytesWritten
    }
    while (true)
    return totalBytesRead.toLong()
  }

  override fun read(dst: ByteBuffer, position: Long): Int {
    return readFromPosition(dst, position)
  }

  private fun readFromPosition(dst: ByteBuffer, position: Long?): Int {
    checkClosed()
    when (ijentOpenedFile) {
      is EelOpenedFile.Reader -> Unit
      is EelOpenedFile.Writer -> throw NonReadableChannelException()
    }
    val readResult = fsBlocking {
      if (position == null) {
        ijentOpenedFile.read(dst)
      }
      else {
        ijentOpenedFile.read(dst, position)
      }
    }.getOrThrowFileSystemException()
    return when (readResult) {
      is EelOpenedFile.Reader.ReadResult.Bytes -> readResult.bytesRead
      is EelOpenedFile.Reader.ReadResult.EOF -> -1
    }
  }

  override fun write(src: ByteBuffer, position: Long): Int {
    return writeToPosition(src, position)
  }

  private fun writeToPosition(src: ByteBuffer, position: Long?): Int {
    checkClosed()
    when (ijentOpenedFile) {
      is EelOpenedFile.Writer -> Unit
      is EelOpenedFile.Reader -> throw NonWritableChannelException()
    }

    val bytesWritten =
      fsBlocking {
        if (position != null) {
          ijentOpenedFile.write(src, position)
        }
        else {
          ijentOpenedFile.write(src)
        }
      }
        .getOrThrowFileSystemException()
    return bytesWritten
  }

  /**
   * The current implementation is a huge compromise that tries to work but can never work reliably.
   *
   * The interface of [MappedByteBuffer] is strictly bound to file descriptors and direct memory.
   * Such an abstraction prevents from having decent memory maps for remote filesystems through IJent.
   *
   * This method downloads the file from the remote location, puts it into a temporary place and returns a memory map for the copied file.
   * It brings several problems:
   * * Better not to copy [map] for huge files, they are downloaded from the server to the client.
   * * Concurrent modifications on the server won't be noticed.
   * * The actual implementation supports only READ_ONLY and PRIVATE mappings.
   *   A READ_WRITE implementation would require a complicated algorithm of synchronization.
   * * The copied file is not removed if the IDE exits abruptly.
   */
  override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer {
    val fileCopyOpenOptions = when (mode) {
      MapMode.PRIVATE -> setOf(StandardOpenOption.READ, StandardOpenOption.WRITE)
      MapMode.READ_ONLY -> setOf(StandardOpenOption.READ)
      MapMode.READ_WRITE -> throw UnsupportedOperationException("MapMode.READ_WRITE is not supported")
      else -> throw UnsupportedOperationException("MapMode $mode is not supported")
    }

    check(ijentOpenedFile is EelOpenedFile.Reader) { "The file must be opened for reading" }

    if (ijentOpenedFile is EelOpenedFile.Writer) {
      thisLogger().error(
        "The file ${this} is opened for writing, but an attempt to write anything to the file won't be reflected in the memory map"
      )
    }

    val fileCopyPath = Files.createTempFile("ijent-memory-map-copy-", null)
    return try {
      fsBlocking {
        downloadWholeFile(fileCopyPath)
      }

      fileCopyPath.fileSystem.provider().newFileChannel(fileCopyPath, fileCopyOpenOptions).use { localCopy ->
        localCopy.map(mode, position, size)
      }
    }
    finally {
      // It's safe to delete the file copy as soon as the memory map is created. Even on Windows.
      // https://stackoverflow.com/questions/11099295/file-flag-delete-on-close-and-memory-mapped-files/11099431#11099431
      try {
        Files.delete(fileCopyPath)
      }
      catch (err: IOException) {
        logger<IjentNioFileSystem>().info(
          "Failed to delete a file copy created for mmap. It does not break the IDE but leaves garbage on the disk. Path: $fileCopyPath",
          err,
        )
      }
    }
  }

  private suspend fun downloadWholeFile(fileCopyPath: Path) {
    ijentOpenedFile as EelOpenedFile.Reader
    Files.newByteChannel(fileCopyPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { outputChannel ->
      val buffer = ByteBuffer.allocate(RECOMMENDED_MAX_PACKET_SIZE)
      var position = 0L
      while (true) {
        // There are classes like `jdk.internal.jimage.BasicImageReader` that create a memory map and keep reading the file
        // with usual methods.
        // The current position in the file should remain the same after the copying.
        when (val r = ijentOpenedFile.read(buffer, position).getOrThrowFileSystemException()) {
          is EelOpenedFile.Reader.ReadResult.Bytes -> {
            position += r.bytesRead
            buffer.flip()
            outputChannel.write(buffer)
            buffer.clear()
          }
          is EelOpenedFile.Reader.ReadResult.EOF -> break
        }
      }
    }
  }

  override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
    checkClosed()
    TODO("Not yet implemented")
  }

  override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? {
    checkClosed()
    TODO("Not yet implemented")
  }

  @Throws(FileSystemException::class)
  override fun implCloseChannel() {
    closeOrigin = Throwable()
    fsBlocking {
      ijentOpenedFile.close()
    }
  }

  @kotlin.jvm.Throws(ClosedChannelException::class)
  private fun checkClosed() {
    val origin = closeOrigin
    if (origin != null) {
      throw ClosedChannelException().apply { initCause(origin) }
    }
  }
}