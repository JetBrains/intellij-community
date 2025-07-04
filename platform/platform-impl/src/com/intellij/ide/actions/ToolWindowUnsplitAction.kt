// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi

internal class ToolWindowUnsplitAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val contentManager = e.getData(ToolWindowContentUi.CONTENT_MANAGER_DATA_KEY) ?: return
    val decorator = e.findNearestDecorator() ?: return

    decorator.unsplit(contentManager.selectedContent)
  }

  override fun update(e: AnActionEvent) {
    val decorator = e.findNearestDecorator()
    val toolWindow = decorator?.toolWindow
    e.presentation.isEnabled = decorator != null && decorator.canUnsplit()
    e.presentation.isVisible = (e.presentation.isEnabled || !e.isFromContextMenu)
                               && toolWindow != null && ToolWindowContentUi.isToolWindowReorderAllowed(toolWindow)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}