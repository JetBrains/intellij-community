// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.ToolWindowSplitContentProviderBean
import com.intellij.ui.content.Content
import javax.swing.SwingConstants

internal abstract class ToolWindowSplitActionBase(
  private val isRight: Boolean,
) : ToolWindowContextMenuActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val splitProvider = ToolWindowSplitContentProviderBean.getForToolWindow(toolWindow.id) ?: return
    val decorator = findNearestDecorator(e) ?: return
    if (content == null) return

    val newContent = splitProvider.createContentCopy(toolWindow.project, content)
    decorator.splitWithContent(newContent, if (isRight) SwingConstants.RIGHT else SwingConstants.BOTTOM, -1)
  }

  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    e.presentation.isEnabledAndVisible = ToolWindowContentUi.isTabsReorderingAllowed(toolWindow) &&
                                         ToolWindowSplitContentProviderBean.getForToolWindow(toolWindow.id) != null
  }
}

internal class ToolWindowSplitRightAction : ToolWindowSplitActionBase(isRight = true)

internal class ToolWindowSplitDownAction : ToolWindowSplitActionBase(isRight = false)