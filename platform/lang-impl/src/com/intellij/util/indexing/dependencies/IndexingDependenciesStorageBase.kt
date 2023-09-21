// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.util.io.ResilientFileChannel
import com.intellij.util.io.createParentDirectories
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

abstract class IndexingDependenciesStorageBase(private val storage: FileChannel,
                                               protected val storagePath: Path,
                                               private val storageVersion: Int) {

  companion object {
    private const val STORAGE_VERSION_OFFSET = 0L
    const val FIRST_UNUSED_OFFSET = STORAGE_VERSION_OFFSET + Int.SIZE_BYTES

    @Throws(IOException::class)
    fun <T> openOrInit(path: Path,
                       storageFactory: (storage: FileChannel, path: Path) -> T): T where T : IndexingDependenciesStorageBase {
      path.createParentDirectories()
      val channel = ResilientFileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)
      val storage = storageFactory(channel, path)

      storage.initIfNotInitialized()

      return storage
    }
  }

  @Throws(IOException::class)
  fun readIntOrExecute(offset: Long, otherwise: (bytesRead: Int) -> Int): Int {
    val fourBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
    val read = storage.read(fourBytes.clear(), offset)
    return if (read == Int.SIZE_BYTES) {
      fourBytes.rewind().getInt()
    }
    else {
      otherwise(read)
    }
  }

  @Throws(IOException::class)
  fun writeIntOrExecute(offset: Long, value: Int, otherwise: (bytesWritten: Int) -> Int) {
    val fourBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
    fourBytes.putInt(value)
    val wrote = storage.write(fourBytes.rewind(), offset)
    if (wrote != Int.SIZE_BYTES) {
      otherwise(wrote)
    }
  }

  @Throws(IOException::class)
  fun checkVersion(onVersionMismatch: (expectedVersion: Int, actualVersion: Int) -> Unit) {
    val actualVersion = readVersion()
    if (actualVersion != storageVersion) {
      onVersionMismatch(storageVersion, actualVersion)
    }
  }

  @Throws(IOException::class)
  private fun readVersion(): Int {
    return readIntOrExecute(STORAGE_VERSION_OFFSET) { bytesRead ->
      throw IOException(tooFewBytesReadMsg(bytesRead, "storage version"))
    }
  }

  @Throws(IOException::class)
  private fun writeVersion() {
    writeIntOrExecute(STORAGE_VERSION_OFFSET, storageVersion) { bytesWritten ->
      throw IOException(tooFewBytesWrittenMsg(bytesWritten, "storage version"))
    }
  }

  fun initIfNotInitialized() {
    val storageNotInitialized = !Files.exists(storagePath) || Files.size(storagePath) == 0L
    if (storageNotInitialized) {
      resetStorage()
    }
  }

  @Throws(IOException::class)
  open fun resetStorage() {
    writeVersion()
  }

  fun completeMigration() {
    writeVersion()
  }

  @Throws(IOException::class)
  fun close() {
    storage.close()
  }

  fun tooFewBytesWrittenMsg(bytes: Int, op: String) = "Could not write $op (only $bytes bytes written). Storage path: $storagePath"
  fun tooFewBytesReadMsg(bytes: Int, op: String) = "Could not read $op (only $bytes bytes read). Storage path: $storagePath"
}