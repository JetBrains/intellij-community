// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class RunToolbarEditConfigurationAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.dataContext.editConfiguration()
  }
}


class RunToolbarRemoveSlotAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      e.id()?.let {
        RunToolbarSlotManager.getInstance(project).removeSlot(it)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project?.let { project ->
      e.runToolbarData() != RunToolbarSlotManager.getInstance(project).mainSlotData
    } ?: false
  }
}

class RunToolbarMoveToTopAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { project ->
      val manager = RunToolbarSlotManager.getInstance(project)
      if (manager.getState().isSinglePlain()) {
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
}