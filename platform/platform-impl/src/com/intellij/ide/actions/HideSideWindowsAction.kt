// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.openapi.wm.impl.getToolWindowManagerForEvent
import com.intellij.toolWindow.ToolWindowEventSource

internal class HideSideWindowsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindowManager = getToolWindowManagerForEvent(e) as? ToolWindowManagerImpl ?: return
    val id = toolWindowManager.activeToolWindowId ?: toolWindowManager.lastActiveToolWindowId ?: return
    val window = toolWindowManager.getToolWindow(id) ?: return
    if (HideToolWindowAction.Manager.shouldBeHiddenByShortCut(window)) {
      toolWindowManager.hideToolWindow(id = id, hideSide = true, source = ToolWindowEventSource.HideSideWindowsAction)
    }
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val toolWindowManager = getToolWindowManagerForEvent(event)
    if (toolWindowManager == null) {
      presentation.isEnabled = false
      return
    }

    if (toolWindowManager.activeToolWindowId == null) {
      val window = toolWindowManager.getToolWindow(toolWindowManager.lastActiveToolWindowId ?: return)
      presentation.isEnabled = window != null && HideToolWindowAction.Manager.shouldBeHiddenByShortCut(window)
    }
    else {
      presentation.isEnabled = true
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}