// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.google.common.hash.HashCode
import com.intellij.util.indexing.dependencies.IndexingDependenciesFingerprint.FingerprintImpl
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path

class AppIndexingDependenciesStorage(private val storage: FileChannel, storagePath: Path) :
  IndexingDependenciesStorageBase(storage, storagePath, CURRENT_STORAGE_VERSION) {

  companion object {
    private const val CURRENT_STORAGE_VERSION = 1
    private const val DEFAULT_INDEXING_REQUEST: Int = 1 // not 0, because all the files should be scanned at least once
    private const val INDEXING_REQUEST_OFFSET = FIRST_UNUSED_OFFSET
    private const val INDEXING_REQUEST_SIZE = Int.SIZE_BYTES
    private const val APP_FINGERPRINT_OFFSET = INDEXING_REQUEST_OFFSET + INDEXING_REQUEST_SIZE
    private const val APP_FINGERPRINT_SIZE = IndexingDependenciesFingerprint.FINGERPRINT_SIZE_IN_BYTES
    private const val FILE_SIZE = APP_FINGERPRINT_OFFSET + APP_FINGERPRINT_SIZE

    private val DEFAULT_FINGERPRINT: FingerprintImpl = IndexingDependenciesFingerprint.NULL_FINGERPRINT

    @Throws(IOException::class)
    fun openOrInit(path: Path): AppIndexingDependenciesStorage {
      return openOrInit(path, ::AppIndexingDependenciesStorage)
    }
  }

  @Throws(IOException::class)
  override fun resetStorage() {
    synchronized(storage) {
      super.resetStorage()
      writeRequestId(DEFAULT_INDEXING_REQUEST)
      writeAppFingerprint(DEFAULT_FINGERPRINT)
      storage.truncate(FILE_SIZE)
      storage.force(false)
    }
  }

  @Throws(IOException::class)
  fun readRequestId(): Int {
    synchronized(storage) {
      return readIntOrExecute(INDEXING_REQUEST_OFFSET) { bytesRead ->
        throw IOException(tooFewBytesReadMsg(bytesRead, "indexing stamp"))
      }
    }
  }

  @Throws(IOException::class)
  fun writeRequestId(requestId: Int) {
    synchronized(storage) {
      writeIntOrExecute(INDEXING_REQUEST_OFFSET, requestId) { bytesWritten ->
        throw IOException(tooFewBytesWrittenMsg(bytesWritten, "indexing stamp"))
      }
      storage.force(false)
    }
  }

  @Throws(IOException::class)
  fun writeAppFingerprint(fingerprint: FingerprintImpl) {
    synchronized(storage) {
      val buffer = ByteBuffer.allocate(APP_FINGERPRINT_SIZE)
      buffer.put(fingerprint.fingerprint.asBytes())
      buffer.rewind()
      val bytesWritten = storage.write(buffer, APP_FINGERPRINT_OFFSET)
      if (bytesWritten != APP_FINGERPRINT_SIZE) {
        throw IOException(tooFewBytesWrittenMsg(bytesWritten, "indexing stamp"))
      }
      storage.force(false)
    }
  }

  @Throws(IOException::class)
  fun readAppFingerprint(): FingerprintImpl {
    synchronized(storage) {
      val buffer = ByteBuffer.allocate(APP_FINGERPRINT_SIZE)
      val bytesRead = storage.read(buffer, APP_FINGERPRINT_OFFSET)
      if (bytesRead != APP_FINGERPRINT_SIZE) {
        throw IOException(tooFewBytesReadMsg(bytesRead, "indexing stamp"))
      }

      val bytes = ByteArray(APP_FINGERPRINT_SIZE)
      buffer.rewind().get(bytes)
      val hashCode = HashCode.fromBytes(bytes)
      return FingerprintImpl(hashCode)
    }
  }
}