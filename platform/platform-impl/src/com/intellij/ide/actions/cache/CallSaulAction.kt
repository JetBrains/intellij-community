// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.util.CachedValueProvider

internal class CallSaulAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) = service<Saul>().sortThingsOut(e.project)
}

internal class CacheRecoveryActionGroup: ComputableActionGroup() {
  override fun createChildrenProvider(actionManager: ActionManager): CachedValueProvider<Array<AnAction>> {
    return CachedValueProvider {
      val actions = arrayOf<AnAction>(CallSaulAction(), Separator.getInstance()) + service<Saul>().sortedActions.map {
        it.toAnAction()
      }.toTypedArray()
      CachedValueProvider.Result.create(actions, service<Saul>().modificationRecoveryActionTracker)
    }
  }

  private fun RecoveryAction.toAnAction(): AnAction {
    val recoveryAction = this
    return object: DumbAwareAction(recoveryAction.presentableName) {
      override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        CacheRecoveryUsageCollector.recordRecoveryPerformedEvent(recoveryAction, false, project)
        recoveryAction.perform(project)
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = recoveryAction.canBeApplied(e.project)
      }
    }
  }
}