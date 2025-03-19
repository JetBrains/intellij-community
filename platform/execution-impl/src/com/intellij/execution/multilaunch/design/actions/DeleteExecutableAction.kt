package com.intellij.execution.multilaunch.design.actions

import com.intellij.openapi.actionSystem.AnActionEvent

internal class DeleteExecutableAction : ManageExecutableAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val viewModel = e.executablesViewModel ?: return
    val executable = e.editableRow?.executable ?: return

    viewModel.removeRow(executable)
  }
}