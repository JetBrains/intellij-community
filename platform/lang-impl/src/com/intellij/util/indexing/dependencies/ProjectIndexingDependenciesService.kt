// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

/**
 * Service that tracks FileIndexingStamp.
 *
 * There are two kinds of tokens: scanning and indexing. Scanning tokens must be explicitly marked as "successfully completed".
 * If there are incomplete or unsuccessful scanning tokens remaining on IDE shutdown, then IDE will do "heavy" scanning on the
 * following start.
 *
 * Indexing tokens are not sensitive to completion. It is expected that during scanning Indexing flag will be cleared for
 * all the files that needs indexing. For files that are sent directly to indexing from VFS refresh we don't need to invalidate
 * indexing flag explicitly, because all these files will have updated modification counter.
 *
 * Notes about "invalidate caches":
 *
 * 1. If VFS is invalidated, we don't need any additional actions. IndexingFlag is stored in the VFS records, invalidating VFS
 * effectively means "reset all the stamps to the default value (unindexed)".
 *
 * 2. If Indexes are invalidated, indexes must call [AppIndexingDependenciesService.invalidateAllStamps], otherwise files that were indexed
 * early will be recognized as "indexed", however real data has been wiped from storages.
 *
 * 3. If int inside [AppIndexingDependenciesService] overflows, invalidate VFS storages will help, because all the files fil be marked as "unindexed", and
 * we don't really care if indexing stamp starts counting from 0, or from -42. We only care that after
 * [AppIndexingDependenciesService.invalidateAllStamps] invocation "expected" and "actual" stamps are different numbers
 *
 * 4. We don't want "invalidate caches" to drop persistent state. It is OK, if the state is dropped together with VFS invalidation,
 * but persistence should not be dropped in other cases, because IndexingStamp is actually stored in VFS.
 *
 */
@Service(Service.Level.PROJECT)
class ProjectIndexingDependenciesService @NonInjectable @VisibleForTesting constructor(storagePath: Path,
                                                                                       private val appIndexingDependenciesService: AppIndexingDependenciesService) : Disposable {
  companion object {

    @JvmStatic
    val NULL_STAMP: FileIndexingStamp = NullIndexingStamp

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

  private val issuedScanningTokens = HashSet<Any>()

  @Volatile
  private var heavyScanningOnProjectOpen: Boolean = false

  private val storage: ProjectIndexingDependenciesStorage = openOrInitStorage(storagePath)

  constructor(project: Project) : this(project.getProjectDataPath("indexingStamp").resolve("indexingStamp.dat"),
                                       application.service<AppIndexingDependenciesService>())

  init {
    try {
      storage.checkVersion { expectedVersion, actualVersion ->
        requestVfsRebuildAndResetStorage(IOException("Incompatible version change in ProjectIndexingDependenciesService: " +
                                                     "$actualVersion to $expectedVersion"))
      }

      heavyScanningOnProjectOpen = storage.readIncompleteScanningMark()
    }
    catch (e: IOException) {
      requestVfsRebuildAndResetStorage(e)
      // we don't rethrow exception, because this will put IDE in unusable state.
    }
  }

  private fun requestVfsRebuildAndResetStorage(reason: IOException) {
    try {
      // TODO-ank: we don't need VFS rebuild. It's enough to rebuild indexing stamp attribute storage
      requestVfsRebuildDueToError(reason)
    }
    finally {
      storage.resetStorage()
    }
  }

  @RequiresBackgroundThread
  fun getLatestIndexingRequestToken(): IndexingRequestToken {
    val appCurrent = appIndexingDependenciesService.getCurrent()
    return IndexingRequestTokenImpl(appCurrent)
  }

  @RequiresBackgroundThread
  fun newScanningToken(): ScanningRequestToken {
    val appCurrent = appIndexingDependenciesService.getCurrent()
    val token = WriteOnlyScanningRequestTokenImpl(appCurrent)
    registerIssuedToken(token)
    return token
  }

  @RequiresBackgroundThread
  fun newScanningTokenOnProjectOpen(): ScanningRequestToken {
    val appCurrent = appIndexingDependenciesService.getCurrent()
    val token = if (heavyScanningOnProjectOpen || issuedScanningTokens.contains(RequestHeavyScanningOnThisOrNextStartToken)) {
      thisLogger().info("Heavy scanning on startup because of incomplete scanning from previous IDE session")
      heavyScanningOnProjectOpen = false
      WriteOnlyScanningRequestTokenImpl(appCurrent)
    }
    else {
      ReadWriteScanningRequestTokenImpl(appCurrent)
    }
    registerIssuedToken(token)
    completeTokenOrFutureToken(RequestHeavyScanningOnThisOrNextStartToken, true)
    return token
  }

  fun newFutureScanningToken(): FutureScanningRequestToken {
    return FutureScanningRequestToken().also { registerIssuedToken(it) }
  }

  private fun registerIssuedToken(token: Any) {
    synchronized(issuedScanningTokens) {
      if (issuedScanningTokens.isEmpty()) {
        storage.writeIncompleteScanningMark(true)
      }
      issuedScanningTokens.add(token)
    }
  }

  fun completeToken(token: FutureScanningRequestToken) {
    completeTokenOrFutureToken(token, token.isSuccessful())
  }

  fun completeToken(token: ScanningRequestToken, isFullScanning: Boolean) {
    if (token.isSuccessful() && isFullScanning) {
      completeTokenOrFutureToken(RequestHeavyScanningOnNextStartToken, true)
    }
    completeTokenOrFutureToken(token, token.isSuccessful())
  }

  private fun completeTokenOrFutureToken(token: Any, successful: Boolean) {
    if (!successful) {
      registerIssuedToken(RequestHeavyScanningOnNextStartToken)
    }
    synchronized(issuedScanningTokens) {
      // ignore repeated "complete" calls
      val removed = issuedScanningTokens.remove(token)
      if (removed && issuedScanningTokens.isEmpty()) {
        storage.writeIncompleteScanningMark(false)
      }
    }
  }

  fun requestHeavyScanningOnProjectOpen(debugReason: String) {
    thisLogger().info("Requesting heavy scanning on project open. Reason: $debugReason")
    registerIssuedToken(RequestHeavyScanningOnThisOrNextStartToken)
  }

  override fun dispose() {
    storage.close()
  }

  /**
   * This token can be thrown away without [completeToken] invocation
   */
  @RequiresBackgroundThread
  @TestOnly
  fun getReadOnlyTokenForTest(): ScanningRequestToken {
    val appCurrent = appIndexingDependenciesService.getCurrent()
    return ReadWriteScanningRequestTokenImpl(appCurrent)
  }
}