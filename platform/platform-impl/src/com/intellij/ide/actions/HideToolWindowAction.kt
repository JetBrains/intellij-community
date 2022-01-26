// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.util.ui.UIUtil

internal class HideToolWindowAction : AnAction(), DumbAware {
  companion object {
    internal fun shouldBeHiddenByShortCut(window: ToolWindow): Boolean {
      return window.isVisible && window.type != ToolWindowType.WINDOWED && window.type != ToolWindowType.FLOATING
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val toolWindowManager = ToolWindowManager.getInstance(e.project ?: return) as ToolWindowManagerImpl
    val id = toolWindowManager.activeToolWindowId ?: toolWindowManager.lastActiveToolWindowId ?: return
    toolWindowManager.hideToolWindow(id = id, hideSide = false, moveFocus = true, source = ToolWindowEventSource.HideToolWindowAction)
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    if (project == null) {
      presentation.isEnabled = false
      return
    }

    val toolWindowManager = ToolWindowManager.getInstance(project)
    val window = (toolWindowManager.activeToolWindowId ?: toolWindowManager.lastActiveToolWindowId)?.let(toolWindowManager::getToolWindow)
    if (window == null) {
      presentation.isEnabled = false
    }
    else if (window.isVisible && UIUtil.isDescendingFrom(IdeFocusManager.getGlobalInstance().focusOwner, window.component)) {
      presentation.isEnabled = true
    }
    else {
      presentation.isEnabled = shouldBeHiddenByShortCut(window)
    }
  }
}