// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.application
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.function.IntConsumer
import kotlin.io.path.deleteIfExists
import kotlin.math.max

/**
 * Service that tracks FileIndexingStamp.
 *
 * Notes about "invalidate caches":
 *
 * 1. If VFS is invalidated, we don't need any additional actions. IndexingFlag is stored in the VFS records, invalidating VFS
 * effectively means "reset all the stamps to the default value (unindexed)".
 *
 * 2. If Indexes are invalidated, indexes must call [ProjectIndexingDependenciesService.invalidateAllStamps], otherwise files that were indexed
 * early will be recognized as "indexed", however real data has been wiped from storages.
 *
 * 3. If int inside [FileIndexingStamp], invalidate VFS storages will help, because all the files fil be marked as "unindexed", and
 * we don't really care if indexing stamp starts counting from 0, or from -42. We only care that after
 * [ProjectIndexingDependenciesService.invalidateAllStamps] invocation "expected" and "actual" stamps are different numbers
 *
 * 4. We don't want "invalidate caches" to drop persistent state. It is OK, if the state is dropped together with VFS invalidation,
 * but persistence should not be dropped in other cases, because IndexingStamp is actually stored in VFS.
 *
 */
@Service(Service.Level.PROJECT)
class ProjectIndexingDependenciesService @NonInjectable @VisibleForTesting constructor(storagePath: Path,
                                                                                       private val appIndexingDependenciesService: AppIndexingDependenciesService) : Disposable {
  companion object {
    private const val NULL_INDEXING_STAMP: Int = 0

    @JvmStatic
    val NULL_STAMP: FileIndexingStamp = object : FileIndexingStamp {
      override fun store(storage: IntConsumer) {
        storage.accept(NULL_INDEXING_STAMP)
      }

      override fun isSame(i: Int): Boolean = false
    }

    private fun requestVfsRebuildDueToError(reason: Throwable) {
      thisLogger().error(reason)
      FSRecords.getInstance().scheduleRebuild(reason.message ?: "Failed to read FileIndexingStamp", reason)
    }

    private fun openOrInitStorage(storagePath: Path): ProjectIndexingDependenciesStorage {
      try {
        return ProjectIndexingDependenciesStorage.openOrInit(storagePath)
      } catch (e: IOException) {
        requestVfsRebuildDueToError(e)
        storagePath.deleteIfExists()
        throw e
      }
    }
  }

  @VisibleForTesting
  data class FileIndexingStampImpl(val stamp: Int) : FileIndexingStamp {
    override fun store(storage: IntConsumer) {
      storage.accept(stamp)
    }

    override fun isSame(i: Int): Boolean {
      return i != NULL_INDEXING_STAMP && i == stamp
    }
  }

  private data class IndexingRequestTokenImpl(val requestId: Int,
                                              val appIndexingRequest: AppIndexingDependenciesToken) : IndexingRequestToken {
    private val appIndexingRequestId = appIndexingRequest.toInt()
    override fun getFileIndexingStamp(file: VirtualFile): FileIndexingStamp {
      val fileStamp = file.modificationStamp
      if (fileStamp == -1L) {
        return NULL_STAMP
      }
      else {
        // we assume that stamp and file.modificationStamp never decrease => their sum only grow up
        // in the case of overflow we hope that new value does not match any previously used value
        // (which is hopefully true in most cases, because (new value)==(old value) was used veeeery long time ago)
        return FileIndexingStampImpl(fileStamp.toInt() + requestId + appIndexingRequestId)
      }
    }

    override fun mergeWith(other: IndexingRequestToken): IndexingRequestToken {
      return IndexingRequestTokenImpl(max(requestId, (other as IndexingRequestTokenImpl).requestId),
                                      appIndexingRequest.mergeWith(other.appIndexingRequest))
    }
  }

  private val current = AtomicReference(IndexingRequestTokenImpl(0, appIndexingDependenciesService.getCurrent()))

  private val storage: ProjectIndexingDependenciesStorage = openOrInitStorage(storagePath)

  constructor(project: Project) : this(project.getProjectDataPath("indexingStamp").resolve("indexingStamp.dat"),
                                       application.service<AppIndexingDependenciesService>())

  init {
    try {
      storage.checkVersion { expectedVersion, actualVersion ->
        requestVfsRebuildAndResetStorage(IOException("Incompatible version change in ProjectIndexingDependenciesService: " +
                                                     "$actualVersion to $expectedVersion"))
      }

      val requestId = storage.readRequestId()
      current.set(IndexingRequestTokenImpl(requestId, appIndexingDependenciesService.getCurrent()))
    }
    catch (e: IOException) {
      requestVfsRebuildAndResetStorage(e)
      // we don't rethrow exception, because this will put IDE in unusable state.
    }
  }

  private fun requestVfsRebuildAndResetStorage(reason: IOException) {
    try {
      requestVfsRebuildDueToError(reason)
    }
    finally {
      storage.resetStorage()
    }
  }

  fun getLatestIndexingRequestToken(): IndexingRequestToken {
    val projectCurrent = current.get()
    val appCurrent = appIndexingDependenciesService.getCurrent()
    return if (appCurrent == projectCurrent.appIndexingRequest) {
      projectCurrent
    }
    else {
      current.updateAndGet { IndexingRequestTokenImpl(it.requestId, appCurrent) }
    }
  }

  fun invalidateAllStamps(): IndexingRequestToken {
    val appCurrent = appIndexingDependenciesService.getCurrent()
    return current.updateAndGet { current ->
      val next = current.requestId + 1
      IndexingRequestTokenImpl(if (next + appCurrent.toInt() != NULL_INDEXING_STAMP) next else next + 1, appCurrent)
    }.also {
      // don't use `it`: current.get() will return just updated value or more up-to-date value
      storage.writeRequestId(current.get().requestId)
    }
  }

  override fun dispose() {
    storage.close()
  }
}