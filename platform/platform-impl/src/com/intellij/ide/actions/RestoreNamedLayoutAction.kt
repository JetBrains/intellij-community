// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager

abstract class RestoreNamedLayoutAction(protected val layoutNameSupplier: () -> @NlsSafe String) : DumbAwareAction() {

  constructor(@NlsSafe layoutName: String) : this({ layoutName })

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val layoutManager = ToolWindowDefaultLayoutManager.getInstance()
    layoutManager.activeLayoutName = layoutNameSupplier()
    ToolWindowManagerEx.getInstanceEx(project).setLayout(layoutManager.getLayoutCopy())
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
    e.presentation.description = ActionsBundle.message("action.RestoreNamedLayout.description", layoutNameSupplier())
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

}
