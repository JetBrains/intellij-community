// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content

internal abstract class ToolWindowMoveToSplitterAction(
  private val isNext: Boolean,
) : ToolWindowContextMenuActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val curDecorator = InternalDecoratorImpl.findNearestDecorator(component) ?: return
    val topDecorator = InternalDecoratorImpl.findTopLevelDecorator(component) ?: return

    val newDecorator = if (isNext) topDecorator.getNextCell(curDecorator) else topDecorator.getPrevCell(curDecorator)
    newDecorator?.requestContentFocus()
  }

  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val topDecorator = InternalDecoratorImpl.findTopLevelDecorator(component)
    e.presentation.isEnabled = topDecorator?.mode?.isSplit == true
    e.presentation.isVisible = (e.presentation.isEnabled || !e.isFromContextMenu) &&
                               ToolWindowContentUi.isTabsReorderingAllowed(toolWindow)

  }
}

internal class ToolWindowMoveToNextSplitterAction : ToolWindowMoveToSplitterAction(isNext = true)

internal class ToolWindowMoveToPreviousSplitterAction : ToolWindowMoveToSplitterAction(isNext = false)