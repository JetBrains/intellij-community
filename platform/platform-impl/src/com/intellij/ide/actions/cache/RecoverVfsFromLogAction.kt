// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.cache

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.toNioPath
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLog
import org.jetbrains.annotations.Nls
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div

class RecoverVfsFromLogAction : RecoveryAction {
  override val performanceRate: Int
    get() = 3
  override val presentableName: String @Nls(capitalization = Nls.Capitalization.Title)
    get() = IdeBundle.message("recover.caches.from.log.recovery.action.name")
  override val actionKey: String
    get() = "recover-from-log"

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean = VfsLog.LOG_VFS_OPERATIONS_ENABLED

  @OptIn(ExperimentalPathApi::class)
  override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    val log = PersistentFS.getInstance().vfsLog
    val positionToRecoverTo = log.context.operationLogStorage.end()
    val cachesDir = FSRecords.getCachesDir().toNioPath()
    val newCachesDir = cachesDir.parent / "recovered-caches"
    newCachesDir.deleteRecursively()
    ApplicationManager.getApplication().runReadAction {
      val result = VfsRecoveryUtils.recoverFromPoint(positionToRecoverTo, log.context, cachesDir, newCachesDir)
      LOG.info(result.toString())
    }
    return emptyList()
  }

  companion object {
    private val LOG = Logger.getInstance(RecoverVfsFromLogAction::class.java)
  }
}