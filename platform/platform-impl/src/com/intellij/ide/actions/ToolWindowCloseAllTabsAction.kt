// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi

internal class ToolWindowCloseAllTabsAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val contentManager = e.getData(ToolWindowContentUi.CONTENT_MANAGER_DATA_KEY) ?: return
    for (content in contentManager.contents) {
      if (content.isCloseable()) {
        contentManager.removeContent(content, true)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val contentManager = e.getData(ToolWindowContentUi.CONTENT_MANAGER_DATA_KEY)
    e.presentation.isEnabled = contentManager != null && contentManager.canCloseAllContents()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}