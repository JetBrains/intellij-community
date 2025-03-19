package com.intellij.execution.multilaunch.design.actions

import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.popups.ExecutableSelectionPopupFactory
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.awt.RelativePoint

class ReplaceExecutableAction : ManageExecutableAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val configuration = e.configuration ?: return
    val viewModel = e.executablesViewModel ?: return
    val popupBounds = e.popupBounds ?: return
    val editableRow = e.editableRow ?: return

    fun handleChosen(executables: List<Executable?>) {
      executables.filterNotNull().forEach { executable ->
        val newRow = ExecutableRow(executable, editableRow.condition, editableRow.disableDebugging)
        viewModel.replaceRow(editableRow, newRow)
      }
    }

    val existingExecutables = viewModel.rows.mapNotNull { it?.executable }
    ExecutableSelectionPopupFactory.getInstance(project)
      .createPopup(configuration, existingExecutables, false, ::handleChosen)
      .apply {
        setMinimumSize(popupBounds.size)
        show(RelativePoint(popupBounds.location))
      }
  }
}