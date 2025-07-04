// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import javax.swing.SwingConstants

internal abstract class ToolWindowSplitAndMoveActionBase(
  private val isVertical: Boolean,
) : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val content = e.guessCurrentContent() ?: return
    val decorator = e.findNearestDecorator() ?: return
    decorator.splitWithContent(content, if (isVertical) SwingConstants.RIGHT else SwingConstants.BOTTOM, -1)
  }

  override fun update(e: AnActionEvent) {
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    val contentManager = e.getData(ToolWindowContentUi.CONTENT_MANAGER_DATA_KEY)
    e.presentation.isEnabledAndVisible = toolWindow != null && isToolWindowSplitAllowed(toolWindow) &&
                                         contentManager != null && contentManager.contentCount > 1
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class ToolWindowSplitAndMoveRightAction : ToolWindowSplitAndMoveActionBase(isVertical = true)

internal class ToolWindowSplitAndMoveDownAction : ToolWindowSplitAndMoveActionBase(isVertical = false)