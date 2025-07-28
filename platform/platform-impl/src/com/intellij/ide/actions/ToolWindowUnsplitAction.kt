// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.Content

internal class ToolWindowUnsplitAction : ToolWindowContextMenuActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val contentManager = content?.manager ?: return
    val decorator = findNearestDecorator(e) ?: return

    decorator.unsplit(contentManager.selectedContent)
  }

  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val decorator = findNearestDecorator(e)
    e.presentation.isEnabled = decorator != null && decorator.canUnsplit()
    e.presentation.isVisible = (e.presentation.isEnabled || !e.isFromContextMenu)
                               && ToolWindowContentUi.isTabsReorderingAllowed(toolWindow)
  }
}