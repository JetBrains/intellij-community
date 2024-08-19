// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.*
import java.nio.file.FileSystemException

internal class IjentNioFileChannel private constructor(
  private val nioFs: IjentNioFileSystem,
  private val ijentOpenedFile: IjentOpenedFile,
  // we keep stacktrace of the cause of closing for troubleshooting
  @Volatile
  private var closeOrigin: Throwable? = null
) : FileChannel() {
  companion object {
    @JvmStatic
    internal suspend fun createReading(nioFs: IjentNioFileSystem, path: IjentPath.Absolute): IjentNioFileChannel =
      IjentNioFileChannel(nioFs, nioFs.ijentFs.openForReading(path).getOrThrowFileSystemException())

    @JvmStatic
    internal suspend fun createWriting(
      nioFs: IjentNioFileSystem,
      options: IjentFileSystemApi.WriteOptions
    ): IjentNioFileChannel =
      IjentNioFileChannel(nioFs, nioFs.ijentFs.openForWriting(options).getOrThrowFileSystemException())

    @JvmStatic
    internal suspend fun createReadingWriting(
      nioFs: IjentNioFileSystem,
      options: IjentFileSystemApi.WriteOptions
    ): IjentNioFileChannel {
      return IjentNioFileChannel(nioFs, nioFs.ijentFs.openForReadingAndWriting(options).getOrThrowFileSystemException())
    }
  }

  override fun read(dst: ByteBuffer): Int {
    return readFromPosition(dst, null)
  }

  override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    checkClosed()
    when (ijentOpenedFile) {
      is IjentOpenedFile.Reader -> Unit
      is IjentOpenedFile.Writer -> throw NonReadableChannelException()
    }

    var totalRead = 0L
    fsBlocking {
      handleThatSmartMultiBufferApi(dsts, offset, length) { buf ->
        val read = when (val res = ijentOpenedFile.read(buf).getOrThrowFileSystemException()) {
          is IjentOpenedFile.Reader.ReadResult.Bytes -> res.bytesRead
          is IjentOpenedFile.Reader.ReadResult.EOF -> return@fsBlocking
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
      is IjentOpenedFile.Writer -> Unit
      is IjentOpenedFile.Reader -> throw NonWritableChannelException()
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
      ijentOpenedFile.seek(newPosition, IjentOpenedFile.SeekWhence.START).getOrThrowFileSystemException()
      this@IjentNioFileChannel
    }
  }

  override fun size(): Long {
    checkClosed()
    return fsBlocking {
      return@fsBlocking when (val type = ijentOpenedFile.stat().getOrThrowFileSystemException().type) {
        is IjentFileInfo.Type.Regular -> type.size
        is IjentFileInfo.Type.Directory, is IjentFileInfo.Type.Other -> throw IOException("This file channel is opened for a directory")
        is IjentPosixFileInfo.Type.Symlink -> throw IllegalStateException("Internal error: symlink should be resolved for a file channel")
      }
    }
  }

  override fun truncate(size: Long): FileChannel = apply {
    checkClosed()
    val file = when (ijentOpenedFile) {
      is IjentOpenedFile.Writer -> ijentOpenedFile
      is IjentOpenedFile.Reader -> throw IOException("File ${ijentOpenedFile.path} is not open for writing")
    }
    val currentSize = this.size()
    fsBlocking {
      if (size < currentSize) {
        try {
          file.truncate(size)
        } catch (e : IjentOpenedFile.Writer.TruncateException) {
          e.throwFileSystemException()
        }
      }
      val currentPosition = file.tell().getOrThrowFileSystemException()
      file.seek(currentPosition.coerceIn(0, size), IjentOpenedFile.SeekWhence.START)
    }
    return this
  }

  override fun force(metaData: Boolean) {
    checkClosed()
    TODO("Not yet implemented")
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
    } while (true)
    return totalBytesWritten.toLong()
  }

  override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long {
    checkClosed()
    if (position < 0) {
      throw IllegalArgumentException("Position $position is negative")
    }
    val buf = ByteBuffer.allocate(count.toInt())
    var currentPosition = 0;
    var totalBytesRead = 0;
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
    } while (true)
    return totalBytesRead.toLong()
  }

  override fun read(dst: ByteBuffer, position: Long): Int {
    return readFromPosition(dst, position)
  }

  private fun readFromPosition(dst: ByteBuffer, position: Long?) : Int {
    checkClosed()
    when (ijentOpenedFile) {
      is IjentOpenedFile.Reader -> Unit
      is IjentOpenedFile.Writer -> throw NonReadableChannelException()
    }
    val readResult = fsBlocking {
      if (position == null) {
        ijentOpenedFile.read(dst)
      } else {
        ijentOpenedFile.read(dst, position)
      }
    }.getOrThrowFileSystemException()
    return when (readResult) {
      is IjentOpenedFile.Reader.ReadResult.Bytes -> readResult.bytesRead
      is IjentOpenedFile.Reader.ReadResult.EOF -> -1
    }
  }

  override fun write(src: ByteBuffer, position: Long): Int {
    return writeToPosition(src, position)
  }

  private fun writeToPosition(src: ByteBuffer, position: Long?): Int {
    checkClosed()
    when (ijentOpenedFile) {
      is IjentOpenedFile.Writer -> Unit
      is IjentOpenedFile.Reader -> throw NonWritableChannelException()
    }

    val bytesWritten =
      fsBlocking {
        if (position != null) {
          ijentOpenedFile.write(src, position)
        } else {
          ijentOpenedFile.write(src)
        }
      }
      .getOrThrowFileSystemException()
    return bytesWritten
  }

  override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer =
    throw UnsupportedOperationException()

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