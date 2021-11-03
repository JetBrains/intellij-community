// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.KillableProcess
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.project.DumbAware

class RunToolbarStopAction : AnAction(AllIcons.Actions.Suspend), DumbAware, RTBarAction {
  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_STABLE

  override fun actionPerformed(e: AnActionEvent) {
    e.environment()?.contentToReuse?.let {
      if (canBeStopped(it)) ExecutionManagerImpl.stopProcess(it)
    }
  }

  override fun setShortcutSet(shortcutSet: ShortcutSet) {}

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.PROCESS
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.environment()?.let {
      it.contentToReuse?.processHandler?.let {
        if(it.isProcessTerminating) {
          e.presentation.icon = AllIcons.Debugger.KillProcess
          e.presentation.description = ExecutionBundle.message("action.terminating.process.progress.kill.description")
        } else {
          e.presentation.icon = templatePresentation.icon
          e.presentation.description = templatePresentation.description
        }
        true
      }
    } ?: false

    if (!RunToolbarProcess.isExperimentalUpdatingEnabled) {
      e.mainState()?.let {
        e.presentation.isEnabledAndVisible = e.presentation.isEnabledAndVisible && checkMainSlotVisibility(it)
      }
    }
  }

  private fun canBeStopped(descriptor: RunContentDescriptor?): Boolean {
    val processHandler = descriptor?.processHandler
    return (processHandler != null && !processHandler.isProcessTerminated
           && (!processHandler.isProcessTerminating
               || processHandler is KillableProcess && (processHandler as KillableProcess).canKillProcess()))
  }
}