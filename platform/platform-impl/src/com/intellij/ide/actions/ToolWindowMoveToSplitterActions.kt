// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.toolWindow.InternalDecoratorImpl

internal abstract class ToolWindowMoveToSplitterAction(
  private val isNext: Boolean,
) : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val curDecorator = InternalDecoratorImpl.findNearestDecorator(component) ?: return
    val topDecorator = InternalDecoratorImpl.findTopLevelDecorator(component) ?: return

    val newDecorator = if (isNext) topDecorator.getNextCell(curDecorator) else topDecorator.getPrevCell(curDecorator)
    newDecorator?.requestContentFocus()
  }

  override fun update(e: AnActionEvent) {
    val component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val topDecorator = InternalDecoratorImpl.findTopLevelDecorator(component)
    val toolWindow = topDecorator?.toolWindow
    e.presentation.isEnabled = topDecorator?.mode?.isSplit == true
    e.presentation.isVisible = (e.presentation.isEnabled || !e.isFromContextMenu) &&
                               toolWindow != null && isToolWindowSplitAllowed(toolWindow)

  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

internal class ToolWindowMoveToNextSplitterAction : ToolWindowMoveToSplitterAction(isNext = true)

internal class ToolWindowMoveToPreviousSplitterAction : ToolWindowMoveToSplitterAction(isNext = false)