// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction

internal class ToolWindowCloseOtherTabsAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(e: AnActionEvent) {
    val curContent = e.guessCurrentContent() ?: return
    val contentManager = e.getData(PlatformDataKeys.CONTENT_MANAGER) ?: return
    for (content in contentManager.contents) {
      if (curContent !== content && content.isCloseable()) {
        contentManager.removeContent(content, true)
      }
    }
    contentManager.setSelectedContent(curContent)
  }

  override fun update(e: AnActionEvent) {
    val curContent = e.guessCurrentContent()
    val contentManager = e.getData(PlatformDataKeys.CONTENT_MANAGER)
    e.presentation.isEnabled = curContent != null &&
                               contentManager != null &&
                               contentManager.canCloseContents() &&
                               contentManager.contents.any { it !== curContent && it.isCloseable() }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}