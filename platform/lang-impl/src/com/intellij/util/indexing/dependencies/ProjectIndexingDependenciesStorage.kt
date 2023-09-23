// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path

class ProjectIndexingDependenciesStorage(private val storage: FileChannel, storagePath: Path) :
  IndexingDependenciesStorageBase(storage, storagePath, CURRENT_STORAGE_VERSION) {

  companion object {
    private const val CURRENT_STORAGE_VERSION = 0
    private const val DEFAULT_INDEXING_STAMP: Int = 0
    private const val INDEXING_STAMP_OFFSET = FIRST_UNUSED_OFFSET
    private const val FILE_SIZE = INDEXING_STAMP_OFFSET + Int.SIZE_BYTES

    @Throws(IOException::class)
    fun openOrInit(path: Path): ProjectIndexingDependenciesStorage {
      return openOrInit(path, ::ProjectIndexingDependenciesStorage)
    }
  }

  @Throws(IOException::class)
  override fun resetStorage() {
    synchronized(storage) {
      super.resetStorage()
      writeRequestId(DEFAULT_INDEXING_STAMP)
      storage.truncate(FILE_SIZE)
      storage.force(false)
    }
  }

  @Throws(IOException::class)
  fun readRequestId(): Int {
    synchronized(storage) {
      return readIntOrExecute(INDEXING_STAMP_OFFSET) { bytesRead ->
        throw IOException(tooFewBytesReadMsg(bytesRead, "indexing stamp"))
      }
    }
  }

  @Throws(IOException::class)
  fun writeRequestId(requestId: Int) {
    synchronized(storage) {
      writeIntOrExecute(INDEXING_STAMP_OFFSET, requestId) { bytesWritten ->
        throw IOException(tooFewBytesWrittenMsg(bytesWritten, "indexing stamp"))
      }
      storage.force(false)
    }
  }
}