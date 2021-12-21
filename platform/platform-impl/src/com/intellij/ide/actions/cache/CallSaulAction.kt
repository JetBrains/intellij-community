// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.SystemProperties

internal class CallSaulAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) = service<Saul>().sortThingsOut(RecoveryScope.createInstance(e))

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }
}

internal class CacheRecoveryActionGroup: ComputableActionGroup() {
  override fun createChildrenProvider(actionManager: ActionManager): CachedValueProvider<Array<AnAction>> {
    return CachedValueProvider {
      isPopup = ApplicationManager.getApplication().isInternal
      val actions = if (isSaulHere) {
        val baseActions = arrayListOf<AnAction>(actionManager.getAction("CallSaul"))

        if (isPopup) {
          baseActions.add(Separator.getInstance())
        }

        (baseActions + service<Saul>().sortedActions.map {
          it.toAnAction()
        }).toTypedArray()
      }
      else emptyArray()
      CachedValueProvider.Result.create(actions, service<Saul>().modificationRecoveryActionTracker)
    }
  }

  private fun RecoveryAction.toAnAction(): AnAction {
    val recoveryAction = this
    return object: DumbAwareAction(recoveryAction.presentableName) {
      override fun actionPerformed(e: AnActionEvent) {
        val scope = RecoveryScope.createInstance(e)
        recoveryAction.performUnderProgress(scope,false)
      }

      override fun update(e: AnActionEvent) {
        if (e.project == null) {
          e.presentation.isEnabledAndVisible = false
          return
        }
        val scope = RecoveryScope.createInstance(e)
        e.presentation.isEnabledAndVisible = recoveryAction.canBeApplied(scope) && ApplicationManager.getApplication().isInternal
      }
    }
  }
}

private val isSaulHere: Boolean
  get() = ApplicationManager.getApplication().isInternal ||
          SystemProperties.getBooleanProperty("idea.cache.recovery.enabled", true)