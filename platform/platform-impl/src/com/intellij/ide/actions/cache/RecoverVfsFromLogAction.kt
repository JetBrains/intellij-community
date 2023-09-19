// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogEx
import org.jetbrains.annotations.Nls

class RecoverVfsFromLogAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    if (!isAvailable) {
      NotificationGroupManager.getInstance().getNotificationGroup("Cache Recovery")
        .createNotification(
          IdeBundle.message("notification.cache.recover.from.log.not.available"),
          IdeBundle.message("notification.cache.recover.from.log.not.enabled"),
          NotificationType.INFORMATION
        )
        .notify(e.project)
      return
    }
    performCacheRecovery(e.project)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  companion object {
    @JvmStatic
    val isAvailable: Boolean = VfsLog.isVfsTrackingEnabled

    internal fun performCacheRecovery(project: Project?) {
      val log = (PersistentFS.getInstance().vfsLog ?: throw IllegalStateException("action called with VfsLog disabled")) as VfsLogEx

      try {
        log.query().use { queryContext ->
          with(service<RecoverVfsFromLogService>()) {
            val recoveryPoint = askToChooseRecoveryPoint(queryContext, project, true)
            if (recoveryPoint == null) {
              return
            }
            queryContext.transferLock().launchRecovery(project, recoveryPoint, true)
          }
        }
      }
      catch (e: Throwable) {
        logger<RecoverVfsFromLogRecoveryAction>().error(e)
      }
    }
  }
}

class RecoverVfsFromLogRecoveryAction : RecoveryAction {
  override val performanceRate: Int
    get() = 3
  override val presentableName: String
    @Nls(capitalization = Nls.Capitalization.Title)
    get() = ActionsBundle.message("action.RecoverCachesFromLog.text")
  override val actionKey: String
    get() = "recover-from-log"

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean = RecoverVfsFromLogAction.isAvailable

  override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    RecoverVfsFromLogAction.performCacheRecovery(recoveryScope.project)
    return emptyList()
  }
}
