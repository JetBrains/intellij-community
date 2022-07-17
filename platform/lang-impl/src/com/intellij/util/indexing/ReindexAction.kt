// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.ide.actions.cache.CacheInconsistencyProblem
import com.intellij.ide.actions.cache.ProjectRecoveryScope
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.DumbUtilImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ReindexAction : RecoveryAction {
  override val performanceRate: Int
    get() = 1000
  override val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String
    get() = LangBundle.message("reindex.project.recovery.action.name")
  override val actionKey: String
    get() = "reindex"

  override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    invokeAndWaitIfNeeded {
      val tumbler = FileBasedIndexTumbler("Reindex recovery action")
      tumbler.turnOff()
      try {
        CorruptionMarker.requestInvalidation()
      }
      finally {
        tumbler.turnOn()
      }
    }
    DumbUtilImpl.waitForSmartMode(recoveryScope.project)

    return emptyList()
  }

  override fun canBeApplied(recoveryScope: RecoveryScope): Boolean = recoveryScope is ProjectRecoveryScope
}