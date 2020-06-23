// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

internal class HideAllToolWindowsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    performAction(e.project ?: return)
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    if (project == null) {
      presentation.isEnabled = false
      return
    }

    val toolWindowManager = ToolWindowManager.getInstance(project) as? ToolWindowManagerEx ?: return
    for (id in toolWindowManager.toolWindowIds) {
      if (HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, id)) {
        presentation.isEnabled = true
        presentation.setText(IdeBundle.message("action.hide.all.windows"), true)
        return
      }
    }

    val layout = toolWindowManager.layoutToRestoreLater
    if (layout != null) {
      presentation.isEnabled = true
      presentation.text = IdeBundle.message("action.restore.windows")
      return
    }
    presentation.isEnabled = false
  }
}

private fun performAction(project: Project) {
  val toolWindowManager = ToolWindowManagerEx.getInstanceEx(project)
  val layout = toolWindowManager.layout.copy()
  // to clear windows stack
  toolWindowManager.clearSideStack()
  //toolWindowManager.activateEditorComponent();
  val ids = toolWindowManager.toolWindowIds
  var hasVisible = false
  for (id in ids) {
    if (HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, id)) {
      toolWindowManager.getToolWindow(id)?.hide(null)
      hasVisible = true
    }
  }
  if (hasVisible) {
    toolWindowManager.layoutToRestoreLater = layout
    toolWindowManager.activateEditorComponent()
  }
  else {
    val restoredLayout = toolWindowManager.layoutToRestoreLater
    if (restoredLayout != null) {
      toolWindowManager.layoutToRestoreLater = null
      toolWindowManager.layout = restoredLayout
    }
  }
}