// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.RecoverVfsFromOperationsLogDialog
import com.intellij.ide.actions.SuggestAutomaticVfsRecoveryDialog
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils.RecoveryPoint
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils.thinOut
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogEx
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogQueryContext
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogQueryContextEx
import com.intellij.platform.ide.bootstrap.hideSplash
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.asSafely
import com.intellij.util.io.delete
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.div
import kotlin.io.path.exists

@ApiStatus.Internal
@ApiStatus.Experimental
@Service
class RecoverVfsFromLogService(val coroutineScope: CoroutineScope) {
  private val isRecoveryRunning = AtomicBoolean(false)
  private val shouldSuggestAutomaticRecovery = AtomicBoolean(
    Registry.get("idea.vfs.log-vfs-operations.suggest-automatic-recovery").asBoolean()
  )
  private val isAutomaticRecoverySuggestionRunning = AtomicBoolean(false)

  private fun tryAcquireAutomaticRecoverySuggestion(): Boolean {
    return isAutomaticRecoverySuggestionRunning.compareAndSet(false, true)
  }

  private fun releaseAutomaticRecoverySuggestion() {
    if (!isAutomaticRecoverySuggestionRunning.compareAndSet(true, false)) {
      LOG.error("failed to reset isAutomaticRecoverySuggestionRunning flag")
    }
  }

  private fun tryAcquireRecovery(): Boolean {
    return isRecoveryRunning.compareAndSet(false, true).also {
      if (!it) LOG.warn("recovery is already running!")
    }
  }

  private fun releaseRecovery() {
    if (!isRecoveryRunning.compareAndSet(true, false)) {
      LOG.error("failed to reset isRecoveryRunning flag")
    }
  }

  @JvmOverloads
  fun suggestAutomaticRecoveryIfAllowed(
    restart: Boolean = true,
    getVfsLog: () -> VfsLogEx? = { PersistentFS.getInstance().vfsLog as VfsLogEx }
  ): Job? {
    val vfsLog = getVfsLog() ?: return null
    if (!shouldSuggestAutomaticRecovery.get() || isRecoveryRunning.get()) return null
    if (!tryAcquireAutomaticRecoverySuggestion()) return null

    val queryContext = vfsLog.query()

    return coroutineScope.launch {
      val recoveryPoints = getRecoveryPoints(queryContext)
      if (recoveryPoints.none()) return@launch
      withContext(Dispatchers.EDT) {
        val dialog = SuggestAutomaticVfsRecoveryDialog(ApplicationManagerEx.getApplicationEx().isRestartCapable) { enableSuggestion ->
          Registry.get("idea.vfs.log-vfs-operations.suggest-automatic-recovery").setValue(enableSuggestion)
          shouldSuggestAutomaticRecovery.set(enableSuggestion)
        }
        dialog.show()
        when (dialog.exitCode) {
          CANCEL_EXIT_CODE -> {}
          OK_EXIT_CODE -> {
            val rpList = recoveryPoints.take(2).toList()
            // choose a second recovery point because it should be safer
            val recoveryPoint = if (rpList.size > 1) rpList[1] else rpList.first()
            queryContext.transferLock().launchRecovery(null, recoveryPoint, restart)
          }
          SuggestAutomaticVfsRecoveryDialog.CHOOSE_RECOVERY_POINT_CODE -> {
            val recoveryPoint = askToChooseRecoveryPoint(queryContext, null, true)
            if (recoveryPoint != null) queryContext.transferLock().launchRecovery(null, recoveryPoint, restart)
          }
          else -> throw IllegalArgumentException("unknown dialog exit code: ${dialog.exitCode}")
        }
      }
    }.also {
      it.invokeOnCompletion {
        queryContext.close()
        releaseAutomaticRecoverySuggestion()
      }
    }
  }

  private fun askToChooseRecoveryPoint(project: Project?, recoveryPoints: Sequence<RecoveryPoint>): RecoveryPoint? = invokeAndWaitIfNeeded {
    val app = ApplicationManagerEx.getApplicationEx()
    val dialog = RecoverVfsFromOperationsLogDialog(project, app.isRestartCapable, recoveryPoints)
    dialog.show()
    return@invokeAndWaitIfNeeded if (!dialog.isOK) null else dialog.selectedRecoveryPoint
  }

  fun askToChooseRecoveryPoint(queryContext: VfsLogQueryContextEx,
                               project: Project?,
                               notifyIfNoPointsAvailable: Boolean): RecoveryPoint? {
    val recoveryPoints = getRecoveryPoints(queryContext)
    if (recoveryPoints.none()) {
      if (notifyIfNoPointsAvailable) {
        NotificationGroupManager.getInstance().getNotificationGroup("Cache Recovery")
          .createNotification(
            IdeBundle.message("notification.cache.recover.from.log.not.available"),
            IdeBundle.message("notification.cache.recover.from.log.no.recovery.points"),
            NotificationType.WARNING
          )
          .notify(project)
      }
      LOG.warn("no recovery points available")
      return null
    }
    return askToChooseRecoveryPoint(project, recoveryPoints)
  }

  private suspend fun runRecovery(queryContext: VfsLogQueryContext,
                                  project: Project?,
                                  recoveryPoint: RecoveryPoint,
                                  restart: Boolean,
                                  calledOnVfsInit: Boolean) {
    val app = ApplicationManagerEx.getApplicationEx()
    withModalProgress(
      if (project != null) ModalTaskOwner.project(project) else ModalTaskOwner.guess(),
      IdeBundle.message("progress.cache.recover.from.logs.title"),
      TaskCancellation.nonCancellable()
    ) {
      LOG.info("recovering a VFS instance as of ${recoveryPoint}...")
      reportRawProgress { reporter ->
        recoverCaches(queryContext, recoveryPoint.point, reporter, calledOnVfsInit)
      }
    }
    if (restart) {
      LOG.info("restarting...")
      app.restart(true)
    }
  }

  /**
   * consumes the context
   */
  fun VfsLogQueryContext.launchRecovery(project: Project?, recoveryPoint: RecoveryPoint, restart: Boolean): Job? {
    if (!tryAcquireRecovery()) {
      close()
      return null
    }
    val job = coroutineScope.launch {
      runRecovery(this@launchRecovery, project, recoveryPoint, restart, false)
    }
    job.invokeOnCompletion {
      close()
      releaseRecovery()
      if (it != null) LOG.error(it)
    }
    return job
  }

  internal companion object {
    private val LOG = Logger.getInstance(RecoverVfsFromLogService::class.java)

    private val cachesDir = FSRecords.getCacheDir()
    private val recoveredCachesDir = cachesDir.parent / "recovered-caches"

    // used only in vfs init
    fun recoverSynchronouslyFromLastRecoveryPoint(queryContext: VfsLogQueryContextEx): Boolean {
      // FIXME this modal should be at the call site, but it's java code that is not friendly to coroutines
      return invokeAndWaitIfNeeded {
        hideSplash()
        runWithModalProgressBlocking(ModalTaskOwner.guess(), IdeBundle.message("progress.cache.recover.from.logs.title"),
                                     TaskCancellation.nonCancellable()) {
          val recoveryPoint = getRecoveryPoints(queryContext).firstOrNull() ?: return@runWithModalProgressBlocking false
          reportRawProgress { reporter ->
            recoverCaches(queryContext, recoveryPoint.point, reporter, true)
          }
          true
        }
      }
    }

    private fun recoverCaches(queryContext: VfsLogQueryContext,
                              point: OperationLogStorage.Iterator,
                              progressReporter: RawProgressReporter?,
                              calledOnVfsInit: Boolean) {
      CacheRecoveryUsageCollector.recordRecoveryFromLogStarted(calledOnVfsInit)

      try {
        // TODO FileBasedIndexTumbler disable indexing while recovery is in progress
        val vfsLogEx = serviceIfCreated<ManagingFS>().asSafely<PersistentFS>()?.vfsLog as? VfsLogEx
        vfsLogEx?.awaitPendingWrites()
        vfsLogEx?.flush()
      } catch (ignored: Throwable) {}

      if (recoveredCachesDir.exists()) {
        LOG.info("old recovered caches directory exists and will be deleted")
        recoveredCachesDir.delete(true)
      }
      val result = VfsRecoveryUtils.recoverFromPoint(point, queryContext, cachesDir, recoveredCachesDir,
                                                     progressReporter = progressReporter)
      LOG.info(result.toString())
      CacheRecoveryUsageCollector.recordRecoveryFromLogFinishedEvent(
        calledOnVfsInit,
        result.recoveryTime.inWholeMilliseconds,
        result.fileStateCounts[VfsRecoveryUtils.RecoveryState.CONNECTED] ?: -1,
        result.fileStateCounts[VfsRecoveryUtils.RecoveryState.BOTCHED] ?: -1,
        result.duplicateChildrenLost,
        result.duplicateChildrenDeduplicated,
        result.recoveredAttributesCount,
        result.droppedObsoleteAttributesCount,
        result.recoveredContentsCount,
        result.lostContentsCount
      )

      LOG.info("creating a storages replacement marker...")
      VfsRecoveryUtils.createStoragesReplacementMarker(cachesDir, recoveredCachesDir)
    }

    private fun getRecoveryPoints(queryContext: VfsLogQueryContextEx): Sequence<RecoveryPoint> {
      return queryContext.getRecoveryPoints().asSequence().thinOut()
    }
  }
}