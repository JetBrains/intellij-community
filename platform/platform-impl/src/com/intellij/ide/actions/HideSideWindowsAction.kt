// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowEventSource

internal class HideSideWindowsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val toolWindowManager = ToolWindowManagerEx.getInstanceEx(project) as ToolWindowManagerImpl
    val id = toolWindowManager.activeToolWindowId ?: toolWindowManager.lastActiveToolWindowId ?: return
    val window = toolWindowManager.getToolWindow(id) ?: return
    if (HideToolWindowAction.shouldBeHiddenByShortCut(window)) {
      toolWindowManager.hideToolWindow(id = id,
                                       hideSide = true,
                                       moveFocus = true,
                                       removeFromStripe = false,
                                       source = ToolWindowEventSource.HideSideWindowsAction)
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
    if (toolWindowManager.activeToolWindowId == null) {
      val window = toolWindowManager.getToolWindow(toolWindowManager.lastActiveToolWindowId ?: return)
      presentation.isEnabled = window != null && HideToolWindowAction.shouldBeHiddenByShortCut(window)
    }
    else {
      presentation.isEnabled = true
    }
  }
}