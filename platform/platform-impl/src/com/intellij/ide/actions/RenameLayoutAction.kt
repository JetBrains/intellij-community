// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RenameLayoutAction(private val layoutName: String) : DumbAwareAction() {

  private val manager: ToolWindowDefaultLayoutManager
    get() = ToolWindowDefaultLayoutManager.getInstance()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Rename.text")
    e.presentation.description = ActionsBundle.message("action.CustomLayoutActionsGroup.Rename.description", layoutName)
    e.presentation.isEnabled =
      e.project != null &&
      layoutName in manager.getLayoutNames()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dialog = LayoutNameInputDialog(
      project,
      IdeBundle.message("dialog.rename.window.layout.prompt"),
      IdeBundle.message("dialog.rename.window.layout.title"),
      IdeBundle.message("button.rename"),
      layoutName,
    )
    dialog.show()
    val newName = dialog.inputString
    if (newName.isNullOrBlank()) {
      return
    }
    manager.renameLayout(layoutName, newName)
  }

}
