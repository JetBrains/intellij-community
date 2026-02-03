// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class DeleteNamedLayoutAction(@NlsSafe private val layoutName: String) : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (
      !MessageDialogBuilder.okCancel(
        IdeBundle.message("title.delete"),
        IdeBundle.message("dialog.delete.window.layout.prompt", layoutName)
      ).yesText(IdeBundle.message("button.delete")).ask(project)
    ) {
      return
    }
    ToolWindowDefaultLayoutManager.getInstance().deleteLayout(layoutName)
  }

  override fun update(e: AnActionEvent) {
    val activeLayout = layoutName == ToolWindowDefaultLayoutManager.getInstance().activeLayoutName
    e.presentation.isEnabled = e.project != null && !activeLayout
    e.presentation.description = when {
      activeLayout -> ActionsBundle.message("action.DeleteNamedLayout.current.description", layoutName)
      else -> ActionsBundle.message("action.DeleteNamedLayout.description", layoutName)
    }

  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}