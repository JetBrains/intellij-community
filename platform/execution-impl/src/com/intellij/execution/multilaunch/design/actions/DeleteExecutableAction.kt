package com.intellij.execution.multilaunch.design.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent

class DeleteExecutableAction : ManageExecutableAction(ActionsBundle.message("action.multilaunch.DeleteExecutableAction.text")) {
  override fun actionPerformed(e: AnActionEvent) {
    val viewModel = e.executablesViewModel ?: return
    val executable = e.editableRow?.executable ?: return

    viewModel.removeRow(executable)
  }
}