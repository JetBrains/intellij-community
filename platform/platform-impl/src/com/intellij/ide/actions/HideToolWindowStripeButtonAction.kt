// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.toolWindow.ToolWindowEventSource

internal class HideToolWindowStripeButtonAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  private val AnActionEvent.toolWindow: ToolWindowImpl?
    get() = getData(PlatformDataKeys.TOOL_WINDOW) as? ToolWindowImpl

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.toolWindow != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = e.toolWindow ?: return
    toolWindow.toolWindowManager.hideToolWindow(
      id = toolWindow.id,
      hideSide = false,
      moveFocus = true,
      removeFromStripe = true,
      source = ToolWindowEventSource.SquareStripeButton,
    )
  }
}
