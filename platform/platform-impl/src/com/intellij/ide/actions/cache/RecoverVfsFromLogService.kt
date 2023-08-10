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
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils.RecoveryPoint
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils.thinOut
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogEx
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogQueryContext
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
    getVfsLog: () -> VfsLog? = { PersistentFS.getInstance().vfsLog }
  ): Job? {
    val vfsLog = getVfsLog() ?: return null
    if (!shouldSuggestAutomaticRecovery.get() || isRecoveryRunning.get()) return null
    if (!tryAcquireAutomaticRecoverySuggestion()) return null

    val queryContext = vfsLog.query()

    return coroutineScope.launch {
      val recoveryPoints = getRecoveryPoints(queryContext)
      if (recoveryPoints == null || recoveryPoints.none()) return@launch
      (vfsLog as VfsLogEx).flush() // write pending data to disk, because vfslog storage will be copied inside recovery util
      withContext(Dispatchers.EDT) {
        val dialog = SuggestAutomaticVfsRecoveryDialog(ApplicationManagerEx.getApplicationEx().isRestartCapable) { enableSuggestion ->
          Registry.get("idea.vfs.log-vfs-operations.suggest-automatic-recovery").setValue(enableSuggestion)
          shouldSuggestAutomaticRecovery.set(enableSuggestion)
        }
        dialog.show()
        when (dialog.exitCode) {
          CANCEL_EXIT_CODE -> {}
          OK_EXIT_CODE -> queryContext.transferLock().launchRecovery(null, recoveryPoints.first(), restart)
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

  fun askToChooseRecoveryPoint(queryContext: VfsLogQueryContext,
                               project: Project?,
                               notifyIfNoPointsAvailable: Boolean): RecoveryPoint? {
    val recoveryPoints = getRecoveryPoints(queryContext)
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

  private suspend fun runRecovery(queryContext: VfsLogQueryContext,
                                  project: Project?,
                                  recoveryPoint: RecoveryPoint,
                                  restart: Boolean = true) {
    val app = ApplicationManagerEx.getApplicationEx()
    withModalProgress(
      if (project != null) ModalTaskOwner.project(project) else ModalTaskOwner.guess(),
      IdeBundle.message("progress.cache.recover.from.logs.title"),
      TaskCancellation.nonCancellable()
    ) {
      LOG.info("recovering a VFS instance as of ${recoveryPoint}...")
      prepareRecoveredCaches(queryContext, recoveryPoint.point, progressReporter!!.rawReporter())
    }
    if (restart) {
      LOG.info("restarting...")
      app.restart(true)
    }
  }

  /**
   * consumes the context
   */
  fun VfsLogQueryContext.launchRecovery(project: Project?, recoveryPoint: RecoveryPoint, restart: Boolean = true): Job? {
    if (!tryAcquireRecovery()) {
      close()
      return null
    }
    val job = coroutineScope.launch {
      runRecovery(this@launchRecovery, project, recoveryPoint, restart)
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

    private val cachesDir = FSRecords.getCachesDir().toNioPath()
    private val recoveredCachesDir = cachesDir.parent / "recovered-caches"

    fun recoverSynchronouslyFromLastRecoveryPoint(queryContext: VfsLogQueryContext, needsConfirmation: Boolean = true): Boolean {
      val runRecovery: Boolean =
        if (needsConfirmation) {
          MessageDialogBuilder.okCancel(
            IdeBundle.message("recover.caches.from.log.recovery.action.name"),
            IdeBundle.message("recover.caches.from.log.not.closed.properly.message")
          ).guessWindowAndAsk()
        }
        else true
      if (!runRecovery) return false;
      // FIXME this modal should be at the call site, but it's java code that is not friendly to coroutines
      return runWithModalProgressBlocking(ModalTaskOwner.guess(), IdeBundle.message("progress.cache.recover.from.logs.title"),
                                          TaskCancellation.nonCancellable()) {
        val recoveryPoint = getRecoveryPoints(queryContext)?.firstOrNull() ?: return@runWithModalProgressBlocking false
        prepareRecoveredCaches(queryContext, recoveryPoint.point, rawProgressReporter)
        true
      }
    }

    private fun prepareRecoveredCaches(queryContext: VfsLogQueryContext,
                                       point: OperationLogStorage.Iterator,
                                       progressReporter: RawProgressReporter?) {
      if (recoveredCachesDir.exists()) {
        LOG.info("old recovered caches directory exists and will be deleted")
        recoveredCachesDir.delete(true)
      }
      val result = VfsRecoveryUtils.recoverFromPoint(point, queryContext, cachesDir, recoveredCachesDir,
                                                     progressReporter = progressReporter)
      LOG.info(result.toString())
      LOG.info("creating a storages replacement marker...")
      VfsRecoveryUtils.createStoragesReplacementMarker(cachesDir, recoveredCachesDir)
    }

    private fun getRecoveryPoints(queryContext: VfsLogQueryContext): Sequence<RecoveryPoint>? {
      val mostRecentPoint = VfsRecoveryUtils.findClosestPrecedingPointWithNoIncompleteOperationsBeforeIt(queryContext::end)
      val recoveryPoints = mostRecentPoint?.let {
        VfsRecoveryUtils.generateRecoveryPointsPriorTo(it.constCopier()).thinOut()
      }
      return recoveryPoints
    }
  }
}