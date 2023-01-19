// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.cache

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.SystemProperties

internal class CallSaulAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) = service<Saul>().sortThingsOut(RecoveryScope.createInstance(e))

  override fun update(e: AnActionEvent) {
    val isEnabled = e.project != null
    e.presentation.isEnabledAndVisible = isEnabled
    if (isEnabled) {
      val recoveryScope = RecoveryScope.createInstance(e)
      if (recoveryScope is FilesRecoveryScope) {
        e.presentation.text = ActionsBundle.message("action.CallSaul.on.file.text", recoveryScope.files.size)
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

internal class CacheRecoveryActionGroup: ActionGroup(), DumbAware {
  init {
    templatePresentation.isPopupGroup = ApplicationManager.getApplication().isInternal
    templatePresentation.isHideGroupIfEmpty = true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) return emptyArray()
    return if (isSaulHere) {
      val baseActions = arrayListOf<AnAction>(e.actionManager.getAction("CallSaul"))

      if (isPopup) {
        baseActions.add(Separator.getInstance())
      }

      (baseActions + service<Saul>().sortedActions.map {
        it.toAnAction()
      }).toTypedArray()
    }
    else emptyArray()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
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

      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
      }
    }
  }
}

private val isSaulHere: Boolean
  get() = ApplicationManager.getApplication().isInternal ||
          SystemProperties.getBooleanProperty("idea.cache.recovery.enabled", true)