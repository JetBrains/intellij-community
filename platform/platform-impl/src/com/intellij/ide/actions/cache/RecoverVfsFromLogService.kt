// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils.RecoveryPoint
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils.thinOut
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogQueryContext
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div

@ApiStatus.Internal
@Service
class RecoverVfsFromLogService(val coroutineScope: CoroutineScope) {
  private val cachesDir = FSRecords.getCachesDir().toNioPath()
  private val recoveredCachesDir = cachesDir.parent / "recovered-caches"
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

  fun suggestAutomaticRecoveryIfAllowed() {
    val vfsLog = PersistentFS.getInstance().vfsLog ?: return
    if (!shouldSuggestAutomaticRecovery.get() || isRecoveryRunning.get()) return
    if (!tryAcquireAutomaticRecoverySuggestion()) return

    val queryContext = vfsLog.query()

    coroutineScope.launch {
      val recoveryPoints = queryContext.getRecoveryPoints()
      if (recoveryPoints == null || recoveryPoints.none()) return@launch
      withContext(Dispatchers.EDT) {
        val dialog = SuggestAutomaticVfsRecoveryDialog(ApplicationManagerEx.getApplicationEx().isRestartCapable) { enableSuggestion ->
          Registry.get("idea.vfs.log-vfs-operations.suggest-automatic-recovery").setValue(enableSuggestion)
          shouldSuggestAutomaticRecovery.set(enableSuggestion)
        }
        dialog.show()
        when (dialog.exitCode) {
          CANCEL_EXIT_CODE -> {}
          OK_EXIT_CODE -> queryContext.transferLock().launchRecoverAndRestart(null, recoveryPoints.first())
          SuggestAutomaticVfsRecoveryDialog.CHOOSE_RECOVERY_POINT_CODE -> {
            val recoveryPoint = queryContext.askToChooseRecoveryPoint(null, true)
            if (recoveryPoint != null) queryContext.transferLock().launchRecoverAndRestart(null, recoveryPoint)
          }
          else -> throw IllegalArgumentException("unknown dialog exit code: ${dialog.exitCode}")
        }
      }
    }.invokeOnCompletion {
      queryContext.close()
      releaseAutomaticRecoverySuggestion()
    }
  }

  private fun VfsLogQueryContext.getRecoveryPoints(): Sequence<RecoveryPoint>? {
    val mostRecentPoint = VfsRecoveryUtils.findClosestPrecedingPointWithNoIncompleteOperationsBeforeIt(::end)
    val recoveryPoints = mostRecentPoint?.let {
      VfsRecoveryUtils.generateRecoveryPointsPriorTo(it.constCopier()).thinOut()
    }
    return recoveryPoints
  }

  private fun askToChooseRecoveryPoint(project: Project?, recoveryPoints: Sequence<RecoveryPoint>): RecoveryPoint? = invokeAndWaitIfNeeded {
    val app = ApplicationManagerEx.getApplicationEx()
    val dialog = RecoverVfsFromOperationsLogDialog(project, app.isRestartCapable, recoveryPoints)
    dialog.show()
    return@invokeAndWaitIfNeeded if (!dialog.isOK) null else dialog.selectedRecoveryPoint
  }

  fun VfsLogQueryContext.askToChooseRecoveryPoint(project: Project?,
                                                  notifyIfNoPointsAvailable: Boolean): RecoveryPoint? {
    val recoveryPoints = getRecoveryPoints()
    if (recoveryPoints == null || recoveryPoints.none()) {
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

  @OptIn(ExperimentalPathApi::class)
  private fun VfsLogQueryContext.prepareRecoveredCaches(point: OperationLogStorage.Iterator, progressReporter: RawProgressReporter) {
    recoveredCachesDir.deleteRecursively()
    val result = VfsRecoveryUtils.recoverFromPoint(point, this, cachesDir, recoveredCachesDir, progressReporter = progressReporter)
    LOG.info(result.toString())
  }

  private suspend fun VfsLogQueryContext.recoverAndRestart(project: Project?, recoveryPoint: RecoveryPoint) {
    val app = ApplicationManagerEx.getApplicationEx()
    withModalProgress(
      if (project != null) ModalTaskOwner.project(project) else ModalTaskOwner.guess(),
      IdeBundle.message("progress.cache.recover.from.logs.title"),
      TaskCancellation.nonCancellable()
    ) {
      LOG.info("recovering a VFS instance as of ${recoveryPoint}...")
      prepareRecoveredCaches(recoveryPoint.point, progressReporter!!.rawReporter())
    }
    LOG.info("creating a storages replacement marker...")
    VfsRecoveryUtils.createStoragesReplacementMarker(cachesDir, recoveredCachesDir)
    LOG.info("restarting...")
    app.restart(true)
  }

  /**
   * consumes the context
   */
  fun VfsLogQueryContext.launchRecoverAndRestart(project: Project?, recoveryPoint: RecoveryPoint): Job? {
    if (!tryAcquireRecovery()) {
      close()
      return null
    }
    val job = coroutineScope.launch {
      recoverAndRestart(project, recoveryPoint)
    }
    job.invokeOnCompletion {
      close()
      releaseRecovery()
      if (it != null) LOG.error(it)
    }
    return job
  }

  internal companion object {
    val LOG = Logger.getInstance(RecoverVfsFromLogService::class.java)
  }
}