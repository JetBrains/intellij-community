// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.Content
import javax.swing.SwingConstants

internal abstract class ToolWindowSplitAndMoveActionBase(
  private val isVertical: Boolean,
) : ToolWindowContextMenuActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    if (content == null) return
    val decorator = findNearestDecorator(e) ?: return
    decorator.splitWithContent(content, if (isVertical) SwingConstants.RIGHT else SwingConstants.BOTTOM, -1)
  }

  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val contentManager = content?.manager
    e.presentation.isEnabledAndVisible = ToolWindowContentUi.isTabsReorderingAllowed(toolWindow) &&
                                         contentManager != null && contentManager.contentCount > 1
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class ToolWindowSplitAndMoveRightAction : ToolWindowSplitAndMoveActionBase(isVertical = true)

internal class ToolWindowSplitAndMoveDownAction : ToolWindowSplitAndMoveActionBase(isVertical = false)