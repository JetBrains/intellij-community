// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.indexing.dependencies.IndexingDependenciesFingerprint.Companion.NULL_FINGERPRINT
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.deleteIfExists
import kotlin.math.max

@Service(Service.Level.APP)
class AppIndexingDependenciesService @NonInjectable @VisibleForTesting constructor(storagePath: Path) : Disposable {
  companion object {
    private val defaultStoragePath = Paths.get(PathManager.getSystemPath(), "caches/indexingStamp.dat")

    private fun requestVfsRebuildDueToError(reason: Throwable) {
      thisLogger().error(reason)
      FSRecords.getInstance().scheduleRebuild(reason.message ?: "Failed to read FileIndexingStamp", reason)
    }

    private fun openOrInitStorage(storagePath: Path): AppIndexingDependenciesStorage {
      try {
        return AppIndexingDependenciesStorage.openOrInit(storagePath)
      }
      catch (e: IOException) {
        requestVfsRebuildDueToError(e)
        storagePath.deleteIfExists()
        throw e
      }
    }
  }

  @Suppress("unused")
  constructor() : this(defaultStoragePath)

  private data class AppIndexingDependenciesTokenImpl(val appIndexingRequestId: Int) : AppIndexingDependenciesToken {
    override fun toInt(): Int = appIndexingRequestId
    override fun mergeWith(other: AppIndexingDependenciesToken): AppIndexingDependenciesToken {
      return AppIndexingDependenciesTokenImpl(max(appIndexingRequestId, (other as AppIndexingDependenciesTokenImpl).appIndexingRequestId))
    }
  }

  private val current = AtomicReference(AppIndexingDependenciesTokenImpl(0))
  private val latestFingerprint = AtomicReference(NULL_FINGERPRINT)

  private val storage: AppIndexingDependenciesStorage = openOrInitStorage(storagePath)

  init {
    try {
      var shouldMigrateV0toV1 = false
      storage.checkVersion { expectedVersion, actualVersion ->
        if (actualVersion == 0 && expectedVersion == 1) {
          shouldMigrateV0toV1 = true
        } else {
          requestVfsRebuildAndResetStorage(IOException("Incompatible version change in AppIndexingDependenciesStorage: " +
                                                       "$actualVersion to $expectedVersion"))
        }
      }
      if (shouldMigrateV0toV1) {
        migrateV0toV1()
      }
      val appIndexingRequestId = storage.readRequestId()
      current.set(AppIndexingDependenciesTokenImpl(appIndexingRequestId))
    }
    catch (e: IOException) {
      requestVfsRebuildAndResetStorage(e)
      // we don't rethrow exception, because this will put IDE in unusable state.
    }
  }

  private fun migrateV0toV1() {
    storage.writeAppFingerprint(NULL_FINGERPRINT)
    storage.completeMigration()
  }

  private fun requestVfsRebuildAndResetStorage(reason: IOException) {
    try {
      // TODO-ank: we don't need VFS rebuild. It's enough to rebuild indexing stamp attribute storage
      requestVfsRebuildDueToError(reason)
    }
    finally {
      storage.resetStorage()
      current.set(AppIndexingDependenciesTokenImpl(0))
      latestFingerprint.set(NULL_FINGERPRINT)
    }
  }

  @RequiresBackgroundThread
  internal fun getCurrent(): AppIndexingDependenciesToken {
    val fingerprint = application.service<IndexingDependenciesFingerprint>().getFingerprint()
    if (latestFingerprint.get() == NULL_FINGERPRINT) {
      latestFingerprint.compareAndSet(NULL_FINGERPRINT, storage.readAppFingerprint())
    }

    val latestFingerprintValue = latestFingerprint.get()
    if (latestFingerprintValue != fingerprint) {
      invalidateAllStamps()
      storage.writeAppFingerprint(fingerprint)
      latestFingerprint.compareAndSet(latestFingerprintValue, fingerprint)
    }

    return current.get()
  }

  fun invalidateAllStamps() {
    val next = current.updateAndGet {
      AppIndexingDependenciesTokenImpl(it.appIndexingRequestId + 1)
    }

    // Assumption is that projectStamp >=0 and appStamp >=0. Their sum can be negative and this is fine (think of it as of unsigned int).
    if (next.appIndexingRequestId < 0) {
      requestVfsRebuildAndResetStorage(IOException("App indexing stamp overflow"))
    }
    else {
      // don't use `next`: current.get() will return just updated value or more up-to-date value which might has already
      // been persisted by another thread
      storage.writeRequestId(current.get().appIndexingRequestId)
    }
  }

  override fun dispose() {
    storage.close()
  }

  @TestOnly
  fun getCurrentTokenInTest(): AppIndexingDependenciesToken = current.get()
}