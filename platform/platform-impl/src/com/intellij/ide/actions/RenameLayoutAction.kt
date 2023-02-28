// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import javax.swing.Action

class RenameLayoutAction(private val layoutName: String) : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.text = ActionsBundle.message("action.CustomLayoutActionsGroup.Rename.text")
    e.presentation.description = if (layoutName == ToolWindowDefaultLayoutManager.DEFAULT_LAYOUT_NAME)
      ActionsBundle.message("action.CustomLayoutActionsGroup.Rename.custom.description")
    else
      ActionsBundle.message("action.CustomLayoutActionsGroup.Rename.description", layoutName)
    e.presentation.isEnabled =
      e.project != null &&
      layoutName != ToolWindowDefaultLayoutManager.DEFAULT_LAYOUT_NAME &&
      layoutName in manager.getLayoutNames()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dialog = object : Messages.InputDialog(
      project,
      IdeBundle.message("dialog.rename.window.layout.prompt"),
      IdeBundle.message("dialog.rename.window.layout.title"),
      null,
      layoutName,
      NonExistingLayoutValidator(),
      arrayOf(IdeBundle.message("button.rename"), IdeBundle.message("button.cancel")),
      1,
    ) {
      init {
        okAction.putValue(Action.NAME, IdeBundle.message("button.rename"))
      }
    }
    dialog.show()
    val newName = dialog.inputString
    if (newName.isNullOrBlank()) {
      return
    }
    manager.renameLayout(layoutName, newName)
  }

}

internal class NonExistingLayoutValidator : InputValidator {
  override fun checkInput(inputString: String?): Boolean = !inputString.isNullOrBlank() && inputString !in manager.getLayoutNames()
  override fun canClose(inputString: String?): Boolean = checkInput(inputString)
}

private val manager get() = ToolWindowDefaultLayoutManager.getInstance()
