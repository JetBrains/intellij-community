// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentOpenedFile
import com.intellij.platform.ijent.fs.IjentPath
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.*
import java.nio.file.FileSystemException

internal class IjentNioFileChannel private constructor(
  private val nioFs: IjentNioFileSystem,
  private val ijentOpenedFile: IjentOpenedFile,
) : FileChannel() {
  companion object {
    @JvmStatic
    internal suspend fun createReading(nioFs: IjentNioFileSystem, path: IjentPath.Absolute): IjentNioFileChannel =
      IjentNioFileChannel(nioFs, nioFs.ijent.fs.fileReader(path).getOrThrowFileSystemException())

    @JvmStatic
    internal suspend fun createWriting(
      nioFs: IjentNioFileSystem,
      path: IjentPath.Absolute,
      append: Boolean,
      creationMode: IjentFileSystemApi.FileWriterCreationMode,
    ): IjentNioFileChannel =
      IjentNioFileChannel(
        nioFs,
        nioFs.ijent.fs.fileWriter(path, append = append, creationMode = creationMode).getOrThrowFileSystemException(),
      )
  }

  override fun read(dst: ByteBuffer): Int {
    when (ijentOpenedFile) {
      is IjentOpenedFile.Reader -> Unit
      is IjentOpenedFile.Writer -> throw NonReadableChannelException()
    }
    return nioFs
      .fsBlocking {
        ijentOpenedFile.read(dst)
      }
      .getOrThrowFileSystemException()
  }

  override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    when (ijentOpenedFile) {
      is IjentOpenedFile.Reader -> Unit
      is IjentOpenedFile.Writer -> throw NonReadableChannelException()
    }

    var totalRead = 0L
    nioFs.fsBlocking {
      handleThatSmartMultiBufferApi(dsts, offset, length) { buf ->
        val read = ijentOpenedFile.read(buf).getOrThrowFileSystemException()
        if (read < 0) {  // A strict comparison.
          return@fsBlocking
        }
        else {
          totalRead += read
        }
      }
    }
    return totalRead
  }

  override fun write(src: ByteBuffer): Int {
    when (ijentOpenedFile) {
      is IjentOpenedFile.Writer -> Unit
      is IjentOpenedFile.Reader -> throw NonWritableChannelException()
    }

    return nioFs
      .fsBlocking {
        ijentOpenedFile.write(src)
      }
      .getOrThrowFileSystemException()
  }

  override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long {
    when (ijentOpenedFile) {
      is IjentOpenedFile.Reader -> throw NonWritableChannelException()
      is IjentOpenedFile.Writer -> Unit
    }

    var totalWritten = 0L
    nioFs.fsBlocking {
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
    if (buffers.isEmpty()) throw IndexOutOfBoundsException()
    if (offset !in 0..<buffers.first().remaining()) throw IndexOutOfBoundsException()
    if (length !in buffers.indices) throw IndexOutOfBoundsException()

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
    TODO("Not yet implemented")
  }

  override fun position(newPosition: Long): FileChannel = apply {
    TODO("Not yet implemented")
  }

  override fun size(): Long {
    TODO("Not yet implemented")
  }

  override fun truncate(size: Long): FileChannel = apply {
    TODO("Not yet implemented")
  }

  override fun force(metaData: Boolean) {
    TODO("Not yet implemented")
  }

  override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long {
    TODO("Not yet implemented")
  }

  override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long {
    TODO("Not yet implemented")
  }

  override fun read(dst: ByteBuffer, position: Long): Int {
    TODO("Not yet implemented")
  }

  override fun write(src: ByteBuffer, position: Long): Int {
    TODO("Not yet implemented")
  }

  override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer =
    throw UnsupportedOperationException()

  override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
    TODO("Not yet implemented")
  }

  override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? {
    TODO("Not yet implemented")
  }

  @Throws(FileSystemException::class)
  override fun implCloseChannel() {
    nioFs.fsBlocking {
      ijentOpenedFile.close()
    }
  }
}