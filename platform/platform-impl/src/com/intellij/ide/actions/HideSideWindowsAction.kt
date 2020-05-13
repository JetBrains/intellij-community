// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

internal class HideSideWindowsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val toolWindowManager = ToolWindowManagerEx.getInstanceEx(project)
    val id = toolWindowManager.activeToolWindowId ?: toolWindowManager.lastActiveToolWindowId ?: return
    if (HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, id)) {
      toolWindowManager.hideToolWindow(id, true)
    }
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    if (project == null) {
      presentation.isEnabled = false
      return
    }

    val toolWindowManager = ToolWindowManager.getInstance(project)
    var id = toolWindowManager.activeToolWindowId
    if (id != null) {
      presentation.isEnabled = true
      return
    }

    id = toolWindowManager.lastActiveToolWindowId
    presentation.isEnabled = id != null && HideToolWindowAction.shouldBeHiddenByShortCut(toolWindowManager, id)
  }
}