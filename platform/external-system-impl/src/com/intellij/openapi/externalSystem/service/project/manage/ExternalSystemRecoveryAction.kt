// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.ide.actions.cache.AsyncRecoveryResult
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.ide.actions.cache.ReopenProjectRecoveryAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.util.containers.forEachLoggingErrors
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExternalSystemRecoveryAction : ReopenProjectRecoveryAction() {

  override val performanceRate: Int
    get() = 0

  override val presentableName: String
    get() = ExternalSystemBundle.message("action.ExternalSystem.RecoveryAction.name")

  override val actionKey: String
    get() = "ExternalSystem.RecoveryAction"

  override suspend fun performAsync(recoveryScope: RecoveryScope): AsyncRecoveryResult {
    val contributors = ArrayList<ExternalSystemRecoveryContributor>()
    ExternalSystemRecoveryContributor.EP_NAME.forEachExtensionSafe {
      contributors.add(it.createContributor())
    }

    contributors.forEachLoggingErrors(thisLogger()) {
      it.beforeClose(recoveryScope)
    }

    WorkspaceModelCacheImpl.invalidateCaches()

    val projectPath = closeProject(recoveryScope)

    contributors.forEachLoggingErrors(thisLogger()) {
      it.afterClose()
    }

    val newRecoveryScope = openProject(projectPath)

    contributors.forEachLoggingErrors(thisLogger()) {
      it.afterOpen(newRecoveryScope)
    }

    return AsyncRecoveryResult(newRecoveryScope, emptyList())
  }
}