// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.ui.content.Content

internal class ToolWindowCloseOtherTabsAction : ToolWindowContextMenuActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val contentManager = content?.manager ?: return
    for (cur in contentManager.contents) {
      if (content !== cur && cur.isCloseable()) {
        contentManager.removeContent(cur, true)
      }
    }
    contentManager.setSelectedContent(content)
  }

  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val contentManager = content?.manager
    e.presentation.isEnabled = content != null &&
                               contentManager != null &&
                               contentManager.canCloseContents() &&
                               contentManager.contents.any { it !== content && it.isCloseable() }
  }
}