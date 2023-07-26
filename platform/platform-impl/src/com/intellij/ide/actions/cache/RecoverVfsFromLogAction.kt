// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import org.jetbrains.annotations.Nls


class RecoverVfsFromLogAction : RecoveryAction {
  override val performanceRate: Int
    get() = 3
  override val presentableName: String
    @Nls(capitalization = Nls.Capitalization.Title)
    get() = IdeBundle.message("recover.caches.from.log.recovery.action.name")
  override val actionKey: String
    get() = "recover-from-log"

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean = VfsLog.isVfsTrackingEnabled

  override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    val log = PersistentFS.getInstance().vfsLog ?: throw IllegalStateException("action called with VfsLog disabled")

    try {
      log.query().use { queryContext ->
        with(service<RecoverVfsFromLogService>()) {
          val recoveryPoint = askToChooseRecoveryPoint(queryContext, recoveryScope.project, true)
          if (recoveryPoint == null) {
            return emptyList()
          }
          queryContext.transferLock().launchRecovery(recoveryScope.project, recoveryPoint)
        }
      }
    }
    catch (e: Throwable) {
      logger<RecoverVfsFromLogAction>().error(e)
    }

    return emptyList()
  }
}