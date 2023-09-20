// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.serviceContainer.NonInjectable
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
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

  private val storage: AppIndexingDependenciesStorage = openOrInitStorage(storagePath)

  init {
    try {
      val appIndexingRequestId = storage.readRequestId()
      current.set(AppIndexingDependenciesTokenImpl(appIndexingRequestId))
    }
    catch (e: IOException) {
      requestVfsRebuildDueToError(e)
      storage.resetStorage()
    }
  }

  internal fun getCurrent(): AppIndexingDependenciesToken = current.get()

  fun invalidateAllStamps() {
    current.updateAndGet {
      AppIndexingDependenciesTokenImpl(it.appIndexingRequestId + 1)
    }
    // current.get() will return just updated value or more up-to-date value
    storage.writeRequestId(current.get().appIndexingRequestId)
  }

  override fun dispose() {
    storage.close()
  }

  @TestOnly
  fun getCurrentTokenInTest(): AppIndexingDependenciesToken = current.get()
}