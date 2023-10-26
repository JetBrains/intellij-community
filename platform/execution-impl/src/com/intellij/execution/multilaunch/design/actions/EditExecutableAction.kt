package com.intellij.execution.multilaunch.design.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

class EditExecutableAction : ManageExecutableAction(ActionsBundle.message("action.multilaunch.EditExecutableAction.text")) {
  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val editableRow = e.editableRow ?: return
    e.presentation.isEnabledAndVisible = editableRow.executable?.supportsEditing ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editableRow = e.editableRow ?: return
    editableRow.executable?.performEdit()
  }
}

