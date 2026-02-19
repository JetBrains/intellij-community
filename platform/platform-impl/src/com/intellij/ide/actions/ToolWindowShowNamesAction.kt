// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.toolWindow.ResizeStripeManager

internal class ToolWindowShowNamesAction : DumbAwareToggleAction() {
  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }
  
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent) = ResizeStripeManager.isShowNames()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    ResizeStripeManager.setShowNames(state)
  }
}