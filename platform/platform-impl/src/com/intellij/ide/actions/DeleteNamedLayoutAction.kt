// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

class DeleteNamedLayoutAction(@NlsSafe private val layoutName: String) : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    ToolWindowDefaultLayoutManager.getInstance().deleteLayout(layoutName)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = layoutName != ToolWindowDefaultLayoutManager.DEFAULT_LAYOUT_NAME &&
                               layoutName != ToolWindowDefaultLayoutManager.getInstance().activeLayoutName
    e.presentation.text = layoutName
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}