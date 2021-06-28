// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.KillableProcess
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

class RunToolbarStopAction : AnAction(AllIcons.Actions.Suspend), DumbAware, RTBarAction {
  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_STABLE

  override fun actionPerformed(e: AnActionEvent) {
    e.environment()?.contentToReuse?.let {
      if (canBeStopped(it)) ExecutionManagerImpl.stopProcess(it)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.environment()?.let {
      if(e.isItRunToolbarMainSlot()
         && !RunToolbarSlotManager.getInstance(it.project).getState().isSingleProcess()
         && !e.isOpened()
      ) return@let false

      e.presentation.icon = e.environment()?.getRunToolbarProcess()?.getStopIcon() ?: templatePresentation.icon
      it.contentToReuse?.let {
        canBeStopped(it)
      }
    } ?: false
  }

  private fun canBeStopped(descriptor: RunContentDescriptor?): Boolean {
    val processHandler = descriptor?.processHandler
    return (processHandler != null && !processHandler.isProcessTerminated
            && (!processHandler.isProcessTerminating
                || processHandler is KillableProcess && (processHandler as KillableProcess).canKillProcess()))
  }
}