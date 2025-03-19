package com.intellij.execution.multilaunch.design.actions

import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.execution.multilaunch.design.popups.ExecutableSelectionPopupFactory
import com.intellij.execution.multilaunch.execution.conditions.ConditionFactory
import com.intellij.execution.multilaunch.execution.executables.Executable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.awt.RelativePoint

internal class AddExecutableAction : ManageExecutableAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val configuration = e.configuration ?: return
    val viewModel = e.executablesViewModel ?: return
    val popupBounds = e.popupBounds ?: return

    fun handleChosen(executables: List<Executable?>) {
      executables.filterNotNull().forEach { executable ->
        val condition = ConditionFactory.getInstance(project).createDefault()
        val row = ExecutableRow(executable, condition, false)
        viewModel.addRow(row)
      }
    }

    val existingExecutables = viewModel.rows.mapNotNull { it?.executable }
    ExecutableSelectionPopupFactory.getInstance(project)
      .createPopup(configuration, existingExecutables, true, ::handleChosen)
      .apply {
        setMinimumSize(popupBounds.size)
        show(RelativePoint(popupBounds.location))
      }
  }
}