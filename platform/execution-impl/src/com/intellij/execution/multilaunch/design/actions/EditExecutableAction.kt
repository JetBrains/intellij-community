package com.intellij.execution.multilaunch.design.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

internal class EditExecutableAction : ManageExecutableAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val editableRow = e.editableRow ?: return
    e.presentation.isEnabledAndVisible = editableRow.executable?.supportsEditing ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editableRow = e.editableRow ?: return
    editableRow.executable?.performEdit()
  }
}