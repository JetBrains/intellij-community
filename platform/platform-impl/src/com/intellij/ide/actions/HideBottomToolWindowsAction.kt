// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowEventSource

internal class HideBottomToolWindowsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindowManager = ToolWindowManagerEx.getInstanceEx(e.project ?: return) as? ToolWindowManagerImpl ?: return
    for (toolWindow in getBottomToolwindows(toolWindowManager)) {
      toolWindowManager.hideToolWindow(id = toolWindow.id, source = ToolWindowEventSource.HideBottomWindowsAction)
    }
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    if (project == null) {
      presentation.isEnabled = false
      return
    }
    val toolWindowManager = ToolWindowManagerEx.getInstanceEx(project)
    presentation.isEnabled = getBottomToolwindows(toolWindowManager).any()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private fun getBottomToolwindows(toolWindowManager: ToolWindowManagerEx): Sequence<ToolWindow> {
  return toolWindowManager.toolWindows.asSequence()
    .filter { it.anchor == ToolWindowAnchor.BOTTOM && HideToolWindowAction.Manager.shouldBeHiddenByShortCut(it) }
}
