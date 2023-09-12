// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.io.ResilientFileChannel
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Service that tracks FileIndexingStamp.
 *
 * Notes about "invalidate caches":
 *
 * 1. If VFS is invalidated, we don't need any additional actions. IndexingFlag is stored in the VFS records, invalidating VFS
 * effectively means "reset all the stamps to the default value (unindexed)".
 *
 * 2. If Indexes are invalidated, indexes must call [FileIndexingStampService.invalidateAllStamps], otherwise files that were indexed
 * early will be recognized as "indexed", however real data has been wiped from storages.
 *
 * 3. If int inside [FileIndexingStamp], invalidate VFS storages will help, because all the files fil be marked as "unindexed", and
 * we don't really care if indexing stamp starts counting from 0, or from -42. We only care that after
 * [FileIndexingStampService.invalidateAllStamps] invocation "expected" and "actual" stamps are different numbers
 *
 * 4. We don't want "invalidate caches" to drop persistent state. It is OK, if the state is dropped together with VFS invalidation,
 * but persistence should not be dropped in other cases, because IndexingStamp is actually stored in VFS.
 *
 */
@Service(Service.Level.APP)
class FileIndexingStampService @NonInjectable @VisibleForTesting constructor(storagePath: Path) : Disposable {
  companion object {
    private const val NULL_INDEXING_STAMP: Int = 0
    private const val CURRENT_STORAGE_VERSION = 0
    private const val STORAGE_VERSION_OFFSET = 0L
    private const val INDEXING_STAMP_OFFSET = STORAGE_VERSION_OFFSET + Int.SIZE_BYTES

    private val defaultStoragePath = Paths.get(PathManager.getSystemPath(), "caches/indexingStamp.dat")

    @JvmStatic
    val NULL_STAMP: FileIndexingStamp = FileIndexingStampImpl(NULL_INDEXING_STAMP)
  }

  private data class FileIndexingStampImpl(val stamp: Int) : FileIndexingStamp {
    override fun toInt(): Int = stamp
  }

  private data class IndexingRequestTokenImpl(val requestId: Int) : IndexingRequestToken {
    override fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp {
      val fileStamp = file.modificationStamp
      if (fileStamp == -1L || requestId == NULL_INDEXING_STAMP) {
        return FileIndexingStampImpl(NULL_INDEXING_STAMP)
      }
      else {
        // we assume that stamp and file.modificationStamp never decrease => their sum only grow up
        // in the case of overflow we hope that new value does not match any previously used value
        // (which is hopefully true in most cases, because (new value)==(old value) was used veeeery long time ago)
        return FileIndexingStampImpl(fileStamp.toInt() + requestId)
      }
    }

    override fun mergeWith(other: IndexingRequestToken): IndexingRequestToken {
      return IndexingRequestTokenImpl(max(requestId, (other as IndexingRequestTokenImpl).requestId))
    }
  }

  // Monotonically increasing. You should not compare this number to other stamps other than for equality (because of possible overflows).
  // Monotonic property is to make sure that value is unique, and it will produce unique value after sum with any other
  // monotonically growing number.
  private val current = AtomicReference(IndexingRequestTokenImpl(NULL_INDEXING_STAMP))

  // Use under synchronized to avoid data corruption
  private val storage = ResilientFileChannel.open(storagePath, CREATE, READ, WRITE)

  constructor() : this(defaultStoragePath)

  init {
    val fourBytes = ByteBuffer.allocate(Int.SIZE_BYTES)
    try {
      val storageVersion = readIntOrExecute(fourBytes, STORAGE_VERSION_OFFSET) { bytesRead ->
        if (bytesRead == -1) {
          resetStorage() // this is new file. Initialize it.
        }
        readIntOrExecute(fourBytes, STORAGE_VERSION_OFFSET) { bytesReadSecondTime ->
          throw IOException("Could not read storage version (only $bytesReadSecondTime bytes read). Storage path: $storagePath")
        }
      }

      if (storageVersion != CURRENT_STORAGE_VERSION) {
        throw IOException("Incompatible version change in FileIndexingStampService: $storageVersion > $CURRENT_STORAGE_VERSION")
      }

      val requestId = readIntOrExecute(fourBytes, INDEXING_STAMP_OFFSET) { bytesRead ->
        throw IOException("Could not read indexing stamp (only $bytesRead bytes read). Storage path: $storagePath")
      }
      current.set(IndexingRequestTokenImpl(requestId))
    }
    catch (ioException: IOException) {
      resetStorage()
      requestVfsRebuildDueToError(ioException)
    }
  }

  private fun readIntOrExecute(fourBytes: ByteBuffer, offset: Long, otherwise: (Int) -> Int): Int {
    val read = storage.read(fourBytes.clear(), offset)
    return if (read == Int.SIZE_BYTES) {
      fourBytes.rewind().getInt()
    }
    else {
      otherwise(read)
    }
  }

  private fun requestVfsRebuildDueToError(reason: Throwable) {
    thisLogger().error(reason)
    FSRecords.getInstance().scheduleRebuild(reason.message ?: "Failed to read FileIndexingStamp", reason)
  }

  private fun resetStorage() {
    // at the moment synchronized is not needed, because resetStorage is only invoked from init
    // but in future this may change. synchronized is only to make this method future-proof.
    synchronized(storage) {
      val fileSize = Int.SIZE_BYTES * 2
      val bytes = ByteBuffer.allocate(fileSize)
      bytes.putInt(CURRENT_STORAGE_VERSION)
      bytes.putInt(NULL_INDEXING_STAMP)
      storage.write(bytes.rewind(), 0)
      storage.truncate(fileSize.toLong())
      storage.force(false)
    }
  }

  fun getLatestIndexingRequestToken(): IndexingRequestToken {
    return current.get()
  }

  fun invalidateAllStamps(): IndexingRequestToken {
    return current.updateAndGet { current ->
      val next = current.requestId + 1
      IndexingRequestTokenImpl(if (next == NULL_INDEXING_STAMP) NULL_INDEXING_STAMP + 1 else next)
    }.also {
      synchronized(storage) {
        val bytes = ByteBuffer.allocate(Int.SIZE_BYTES)
        // don't use `it`: current.get() will return just updated value or more up-to-date value
        bytes.putInt(current.get().requestId)
        val wrote = storage.write(bytes.rewind(), INDEXING_STAMP_OFFSET)
        thisLogger().assertTrue(wrote == Int.SIZE_BYTES, "Could not write new indexing stamp (only $wrote bytes written)")
        storage.force(false)
      }
    }
  }

  override fun dispose() {
    storage.close()
  }
}