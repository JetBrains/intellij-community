// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

internal class RunToolbarEditConfigurationAction : DumbAwareAction() {
  companion object {
    const val ACTION_ID = "RunToolbarEditConfigurationAction"
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.dataContext.editConfiguration()
  }
}

internal class RunToolbarShowToolWindowTab : DumbAwareAction() {
  companion object {
    const val ACTION_ID = "RunToolbarShowToolWindowTab"
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      e.project?.let { project ->

        val manager = RunToolbarSlotManager.getInstance(project)
        if (e.runToolbarData() != manager.mainSlotData || e.mainState() == RunToolbarMainSlotState.PROCESS) {
          e.environment()?.let {
            ToolWindowManager.getInstance(project).getToolWindow(it.contentToReuse?.contentToolWindowId ?: it.executor.id)?.let {
              val contentManager = it.contentManager
              contentManager.contents.firstOrNull { it.executionId == it.executionId }?.let {
                true
              } ?: false
            } ?: false

          } ?: false
        }
        else false
      } ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.environment()?.showToolWindowTab()
  }
}


internal class RunToolbarRemoveSlotAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      e.id()?.let {
        RunToolbarSlotManager.getInstance(project).removeSlot(it)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project?.let { project ->
      val slotManager = RunToolbarSlotManager.getInstance(project)
      e.runToolbarData() != slotManager.mainSlotData || (slotManager.slotsCount() != 0 && e.mainState() != RunToolbarMainSlotState.INFO)
    } ?: false
  }
  
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

internal class RunToolbarMoveToTopAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      val manager = RunToolbarSlotManager.getInstance(project)
      if (manager.getState().isSinglePlain() && manager.mainSlotData == e.runToolbarData()) {
        manager.activeProcesses.activeSlots.firstOrNull()?.let {
          manager.moveToTop(it.id)
        }
      }
      else {
        e.id()?.let {
          manager.moveToTop(it)
        }
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project?.let { project ->
      val manager = RunToolbarSlotManager.getInstance(project)
      e.runToolbarData() != manager.mainSlotData
      || manager.getState().isSinglePlain()
    } ?: false
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}