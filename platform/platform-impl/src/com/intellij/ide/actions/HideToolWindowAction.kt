// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.impl.ToolWindowEventSource
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.util.ui.UIUtil

internal class HideToolWindowAction : AnAction(), DumbAware {
  companion object {
    internal fun shouldBeHiddenByShortCut(manager: ToolWindowManager, id: String): Boolean {
      val window = manager.getToolWindow(id)
      return window != null && window.isVisible && window.type != ToolWindowType.WINDOWED && window.type != ToolWindowType.FLOATING
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val toolWindowManager = ToolWindowManager.getInstance(e.project ?: return) as ToolWindowManagerImpl
    val id = toolWindowManager.activeToolWindowId ?: toolWindowManager.lastActiveToolWindowId ?: return
    toolWindowManager.hideToolWindow(id, false, true, ToolWindowEventSource.HideToolWindowAction)
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val project = event.project
    if (project == null) {
      presentation.isEnabled = false
      return
    }

    val toolWindowManager = ToolWindowManager.getInstance(project)
    val id = toolWindowManager.activeToolWindowId ?: toolWindowManager.lastActiveToolWindowId
    val window = if (id == null) null else toolWindowManager.getToolWindow(id)
    if (window == null) {
      presentation.isEnabled = false
      return
    }

    if (window.isVisible && UIUtil.isDescendingFrom(IdeFocusManager.getGlobalInstance().focusOwner, window.component)) {
      presentation.isEnabled = true
    }
    else {
      presentation.isEnabled = shouldBeHiddenByShortCut(toolWindowManager, id!!)
    }
  }
}