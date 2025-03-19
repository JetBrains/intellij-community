// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Path

@ApiStatus.Internal
class ProjectIndexingDependenciesStorage(private val storage: FileChannel, storagePath: Path) :
  IndexingDependenciesStorageBase(storage, storagePath, CURRENT_STORAGE_VERSION) {

  companion object {
    private const val CURRENT_STORAGE_VERSION = 1
    private const val DEFAULT_INCOMPLETE_SCANNING_MARK: Boolean = false
    private const val INCOMPLETE_SCANNING_MARK_OFFSET = FIRST_UNUSED_OFFSET
    const val DEFAULT_APP_INDEXING_REQUEST_ID_OF_LAST_COMPLETED_SCANNING = -1
    private const val APP_INDEXING_REQUEST_ID_OF_LAST_COMPLETED_SCANNING_OFFSET = INCOMPLETE_SCANNING_MARK_OFFSET + Int.SIZE_BYTES
    private const val FILE_SIZE = APP_INDEXING_REQUEST_ID_OF_LAST_COMPLETED_SCANNING_OFFSET + Int.SIZE_BYTES

    @Throws(IOException::class)
    fun openOrInit(path: Path): ProjectIndexingDependenciesStorage {
      return openOrInit(path, ::ProjectIndexingDependenciesStorage)
    }
  }

  @Throws(IOException::class)
  override fun resetStorage() {
    synchronized(storage) {
      super.resetStorage()
      writeIncompleteScanningMark(DEFAULT_INCOMPLETE_SCANNING_MARK)
      writeAppIndexingRequestIdOfLastScanning(DEFAULT_APP_INDEXING_REQUEST_ID_OF_LAST_COMPLETED_SCANNING)
      storage.truncate(FILE_SIZE)
      storage.force(false)
    }
  }

  @Throws(IOException::class)
  fun readIncompleteScanningMark(): Boolean {
    synchronized(storage) {
      return readIntOrExecute(INCOMPLETE_SCANNING_MARK_OFFSET) { bytesRead ->
        throw IOException(tooFewBytesReadMsg(bytesRead, "incomplete scanning mark"))
      } != 0
    }
  }

  @Throws(IOException::class)
  fun writeIncompleteScanningMark(mark: Boolean) {
    synchronized(storage) {
      writeIntOrExecute(INCOMPLETE_SCANNING_MARK_OFFSET, if (mark) 1 else 0) { bytesWritten ->
        throw IOException(tooFewBytesWrittenMsg(bytesWritten, "incomplete scanning mark"))
      }
      storage.force(false)
    }
  }

  fun writeAppIndexingRequestIdOfLastScanning(appIndexingRequestId: Int) {
    synchronized(storage) {
      writeIntOrExecute(APP_INDEXING_REQUEST_ID_OF_LAST_COMPLETED_SCANNING_OFFSET, appIndexingRequestId) { bytesWritten ->
        throw IOException(tooFewBytesWrittenMsg(bytesWritten, "app indexing request id"))
      }
      storage.force(false)
    }
  }

  @Throws(IOException::class)
  fun readAppIndexingRequestIdOfLastScanning(): Int {
    synchronized(storage) {
      return readIntOrExecute(APP_INDEXING_REQUEST_ID_OF_LAST_COMPLETED_SCANNING_OFFSET) { bytesRead ->
        throw IOException(tooFewBytesReadMsg(bytesRead, "app indexing request id"))
      }
    }
  }
}