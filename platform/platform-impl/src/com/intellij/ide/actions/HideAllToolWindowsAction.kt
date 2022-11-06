// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowEventSource

internal class HideAllToolWindowsAction : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val toolWindowManager = ToolWindowManager.getInstance(event.project ?: return) as? ToolWindowManagerImpl ?: return
    val idsToHide = getIdsToHide(toolWindowManager)
    if (idsToHide.none()) {
      toolWindowManager.layoutToRestoreLater?.let {
        toolWindowManager.layoutToRestoreLater = null
        toolWindowManager.setLayout(it)
      }
    }
    else {
      val layout = toolWindowManager.getLayout().copy()
      toolWindowManager.clearSideStack()
      for (id in idsToHide) {
        toolWindowManager.hideToolWindow(id = id, source = ToolWindowEventSource.HideAllWindowsAction)
      }
      toolWindowManager.layoutToRestoreLater = layout
      toolWindowManager.activateEditorComponent()
    }
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.isEnabled = false
    val toolWindowManager = ToolWindowManager.getInstance(event.project ?: return) as ToolWindowManagerEx
    if (getIdsToHide(toolWindowManager).any()) {
      presentation.isEnabled = true
      presentation.putClientProperty(MaximizeEditorInSplitAction.CURRENT_STATE_IS_MAXIMIZED_KEY, false)
      presentation.text = IdeBundle.message("action.hide.all.windows")
    }
    else if (toolWindowManager.layoutToRestoreLater != null) {
      presentation.isEnabled = true
      presentation.text = IdeBundle.message("action.restore.windows")
      presentation.putClientProperty(MaximizeEditorInSplitAction.CURRENT_STATE_IS_MAXIMIZED_KEY, true)
      return
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT
}

private fun getIdsToHide(toolWindowManager: ToolWindowManagerEx): Sequence<String> {
  return toolWindowManager.toolWindows.asSequence()
    .filter { HideToolWindowAction.shouldBeHiddenByShortCut(it) }
    .map { it.id }
}