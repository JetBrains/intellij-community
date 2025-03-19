// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesStorage.Companion.DEFAULT_APP_INDEXING_REQUEST_ID_OF_LAST_COMPLETED_SCANNING
import org.jetbrains.annotations.ApiStatus
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
@ApiStatus.Internal
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
        //FIXME [AK/LK]: don't invalidate VFS if something wrong with indexingStamp -- invalidate indexingStamp itself
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
      var shouldMigrateV0toV1 = false
      storage.checkVersion { expectedVersion, actualVersion ->
        if (expectedVersion == 1 && actualVersion == 0) {
          shouldMigrateV0toV1 = true
        } else {
          requestVfsRebuildAndResetStorage(IOException("Incompatible version change in ProjectIndexingDependenciesService: " +
                                                       "$actualVersion to $expectedVersion"))
        }
      }
      if (shouldMigrateV0toV1) {
        migrateV0toV1()
      }

      heavyScanningOnProjectOpen = storage.readIncompleteScanningMark()
    }
    catch (e: IOException) {
      requestVfsRebuildAndResetStorage(e)
      // we don't rethrow exception, because this will put IDE in unusable state.
    }
  }

  private fun migrateV0toV1() {
    storage.writeAppIndexingRequestIdOfLastScanning(DEFAULT_APP_INDEXING_REQUEST_ID_OF_LAST_COMPLETED_SCANNING)
    storage.completeMigration()
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

  fun isScanningAndIndexingCompleted(): Boolean = !storage.readIncompleteScanningMark()

  fun getAppIndexingRequestIdOfLastScanning(): Int = storage.readAppIndexingRequestIdOfLastScanning()

  @RequiresBackgroundThread
  fun newScanningToken(): ScanningRequestToken {
    val appCurrent = appIndexingDependenciesService.getCurrent()
    val token = WriteOnlyScanningRequestTokenImpl(appCurrent, false)
    registerIssuedToken(token)
    return token
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  fun newScanningTokenOnProjectOpen(allowCheckingForOutdatedIndexesUsingFileModCount: Boolean): ScanningRequestToken {
    val appCurrent = appIndexingDependenciesService.getCurrent()
    val token = if (heavyScanningOnProjectOpen || issuedScanningTokens.contains(RequestFullHeavyScanningToken)) {
      thisLogger().info("Heavy scanning on startup because of incomplete scanning from previous IDE session")
      heavyScanningOnProjectOpen = false
      WriteOnlyScanningRequestTokenImpl(appCurrent, allowCheckingForOutdatedIndexesUsingFileModCount)
    }
    else {
      ReadWriteScanningRequestTokenImpl(appCurrent, allowCheckingForOutdatedIndexesUsingFileModCount)
    }
    registerIssuedToken(token)
    completeTokenOrFutureToken(RequestFullHeavyScanningToken, null, true)
    return token
  }

  fun newIncompleteTaskToken(): IncompleteTaskToken {
    return IncompleteTaskToken().also { registerIssuedToken(it) }
  }

  fun newIncompleteIndexingToken(): IncompleteIndexingToken {
    return IncompleteIndexingToken().also { registerIssuedToken(it) }
  }

  private fun registerIssuedToken(token: Any) {
    thisLogger().info("Register issued token: $token")
    synchronized(issuedScanningTokens) {
      if (issuedScanningTokens.isEmpty() && storage.isOpen) {
        thisLogger().info("Write incomplete scanning mark=true for token: $token")
        storage.writeIncompleteScanningMark(true)
      }
      issuedScanningTokens.add(token)
    }
  }

  fun completeToken(token: IncompleteIndexingToken) {
    completeTokenOrFutureToken(token, null, token.isSuccessful())
  }

  fun completeToken(token: IncompleteTaskToken) {
    completeTokenOrFutureToken(token, null, true)
  }

  fun completeToken(token: ScanningRequestToken, isFullScanning: Boolean) {
    if (token.isSuccessful() && isFullScanning) {
      completeTokenOrFutureToken(RequestFullHeavyScanningToken, null, true)
    }
    completeTokenOrFutureToken(token, token.appIndexingRequestId, token.isSuccessful())
  }

  private fun completeTokenOrFutureToken(token: Any, lastAppIndexingRequestId: AppIndexingDependenciesToken?, successful: Boolean) {
    thisLogger().info("Complete token: ${token}, successful: $successful")
    if (!successful) {
      registerIssuedToken(RequestFullHeavyScanningToken)
    }
    synchronized(issuedScanningTokens) {
      // ignore repeated "complete" calls
      val removed = issuedScanningTokens.remove(token)
      if (removed && issuedScanningTokens.isEmpty() && storage.isOpen) {
        thisLogger().info("Write incomplete scanning mark=false for token: $token")
        storage.writeIncompleteScanningMark(false)
      }
      if (lastAppIndexingRequestId != null && storage.isOpen) {
        // Write each time, not only after the last token has completed, because the last completed token
        // might be an IncompleteTaskToken. Then lastAppIndexingRequestId will be null.
        storage.writeAppIndexingRequestIdOfLastScanning(lastAppIndexingRequestId.toInt())
      }
    }
  }

  fun requestHeavyScanningOnProjectOpen(debugReason: String) {
    thisLogger().info("Requesting heavy scanning on project open. Reason: $debugReason")
    registerIssuedToken(RequestFullHeavyScanningToken)
  }

  override fun dispose() {
    synchronized(issuedScanningTokens) {
      // inside synchronized(issuedScanningTokens) storage.isOpen check should always return the same value
      // to avoid ClosedChannelException from storage.writeIncompleteScanningMark(...)
      storage.close()
    }
  }

  /**
   * This token can be thrown away without [completeToken] invocation
   */
  @RequiresBackgroundThread
  @TestOnly
  fun getReadOnlyTokenForTest(): ScanningRequestToken {
    val appCurrent = appIndexingDependenciesService.getCurrent()
    return ReadWriteScanningRequestTokenImpl(appCurrent, true)
  }
}