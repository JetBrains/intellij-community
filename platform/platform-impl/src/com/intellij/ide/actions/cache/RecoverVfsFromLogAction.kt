// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.RecoverVfsFromOperationsLogDialog
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.openapi.vfs.newvfs.persistent.log.IteratorUtils.constCopier
import com.intellij.openapi.vfs.newvfs.persistent.log.OperationLogStorage
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div


@ApiStatus.Internal
class RecoverVfsFromLogService(val coroutineScope: CoroutineScope)

class RecoverVfsFromLogAction : RecoveryAction {
  override val performanceRate: Int
    get() = 3
  override val presentableName: String
    @Nls(capitalization = Nls.Capitalization.Title)
    get() = IdeBundle.message("recover.caches.from.log.recovery.action.name")
  override val actionKey: String
    get() = "recover-from-log"

  private val cachesDir = FSRecords.getCachesDir().toNioPath()
  private val recoveredCachesDir = cachesDir.parent / "recovered-caches"

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean = VfsLog.LOG_VFS_OPERATIONS_ENABLED

  override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    val log = PersistentFS.getInstance().vfsLog
    val app = ApplicationManagerEx.getApplicationEx()

    val recoveryPoints = VfsRecoveryUtils.goodRecoveryPointsBefore(log.context.operationLogStorage.end().constCopier())
    if (recoveryPoints.none()) {
      NotificationGroupManager.getInstance().getNotificationGroup("Cache Recovery")
        .createNotification(
          IdeBundle.message("notification.cache.recover.from.log.not.available"),
          IdeBundle.message("notification.cache.recover.from.log.no.recovery.points"),
          NotificationType.WARNING
        )
        .notify(recoveryScope.project)
      LOG.warn("no recovery points available")
      return emptyList()
    }

    val recoveryPoint: VfsRecoveryUtils.RecoveryPoint = invokeAndWaitIfNeeded {
      val dialog = RecoverVfsFromOperationsLogDialog(recoveryScope.project, app.isRestartCapable, recoveryPoints)
      dialog.show()
      if (!dialog.isOK) return@invokeAndWaitIfNeeded null

      return@invokeAndWaitIfNeeded dialog.selectedRecoveryPoint
    } ?: return emptyList()

    service<RecoverVfsFromLogService>().coroutineScope.launch {
      withModalProgress(
        ModalTaskOwner.project(recoveryScope.project),
        IdeBundle.message("progress.cache.recover.from.logs.title"),
        TaskCancellation.nonCancellable()
      ) {
        LOG.info("recovering a VFS instance as of ${recoveryPoint}...")
        prepareRecoveredCaches(log.context, recoveryPoint.point, progressReporter!!.rawReporter())
      }
      LOG.info("creating a storages replacement marker...")
      VfsRecoveryUtils.createStoragesReplacementMarker(cachesDir, recoveredCachesDir)
      LOG.info("restarting...")
      app.restart(true)
    }
    return emptyList()
  }

  @OptIn(ExperimentalPathApi::class)
  fun prepareRecoveredCaches(logContext: VfsLogContext, point: OperationLogStorage.Iterator, progressReporter: RawProgressReporter) {
    recoveredCachesDir.deleteRecursively()
    val result = VfsRecoveryUtils.recoverFromPoint(point, logContext, cachesDir, recoveredCachesDir, progressReporter = progressReporter)
    LOG.info(result.toString())
  }

  companion object {
    private val LOG = Logger.getInstance(RecoverVfsFromLogAction::class.java)
  }
}