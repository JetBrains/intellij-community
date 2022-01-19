// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowEventSource
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl

internal class HideAllToolWindowsAction : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val toolWindowManager = ToolWindowManager.getInstance(event.project ?: return) as? ToolWindowManagerImpl ?: return
    val idsToHide = getIdsToHide(toolWindowManager)
    val window = event.getData(EditorWindow.DATA_KEY)
    if (window != null && window.owner.isFloating) {
      return
    }

    if (idsToHide.none()) {
      val restoredLayout = toolWindowManager.layoutToRestoreLater
      if (restoredLayout != null) {
        toolWindowManager.layoutToRestoreLater = null
        toolWindowManager.setLayout(restoredLayout)
      }
    }
    else {
      val layout = toolWindowManager.getLayout().copy()
      toolWindowManager.clearSideStack()
      for (id in idsToHide) {
        toolWindowManager.hideToolWindow(id = id,
                          hideSide = false,
                          moveFocus = true,
                          removeFromStripe = false,
                          source = ToolWindowEventSource.HideAllWindowsAction)
      }
      toolWindowManager.layoutToRestoreLater = layout
      toolWindowManager.activateEditorComponent()
    }
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    presentation.isEnabled = false
    val window = event.getData(EditorWindow.DATA_KEY)
    if (window != null && window.owner.isFloating) {
      return
    }

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
}

private fun getIdsToHide(toolWindowManager: ToolWindowManagerEx): Sequence<String> {
  return toolWindowManager.toolWindows.asSequence()
    .filter { HideToolWindowAction.shouldBeHiddenByShortCut(it) }
    .map { it.id }
}