// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.ToolWindowSplitContentProviderBean
import javax.swing.SwingConstants

internal abstract class ToolWindowSplitActionBase(
  private val isVertical: Boolean,
) : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW) ?: return
    val splitProvider = ToolWindowSplitContentProviderBean.getForToolWindow(toolWindow.id) ?: return
    val decorator = e.findNearestDecorator() ?: return
    val selectedContent = e.guessCurrentContent() ?: return

    val newContent = splitProvider.createContentCopy(toolWindow.project, selectedContent)
    decorator.splitWithContent(newContent, if (isVertical) SwingConstants.RIGHT else SwingConstants.BOTTOM, -1)
  }

  override fun update(e: AnActionEvent) {
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    e.presentation.isEnabledAndVisible = toolWindow != null && ToolWindowContentUi.isToolWindowReorderAllowed(toolWindow) &&
                                         ToolWindowSplitContentProviderBean.getForToolWindow(toolWindow.id) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class ToolWindowSplitRightAction : ToolWindowSplitActionBase(isVertical = true)

internal class ToolWindowSplitDownAction : ToolWindowSplitActionBase(isVertical = false)