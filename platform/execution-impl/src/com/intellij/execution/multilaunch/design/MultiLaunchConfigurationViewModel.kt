package com.intellij.execution.multilaunch.design

import com.intellij.openapi.project.Project
import com.intellij.execution.multilaunch.MultiLaunchConfiguration
import com.intellij.execution.multilaunch.execution.executables.Executable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MultiLaunchConfigurationViewModel(val project: Project, val configuration: MultiLaunchConfiguration) {
  val rows = mutableListOf<ExecutableRow?>()
  val tableModel = ExecutablesTableModel(this)
  var activateAllToolWindows = false

  fun reset() {
    while (tableModel.rowCount > 0) {
      tableModel.removeRow(0)
    }
    // 'null' stands for 'Add' button
    tableModel.addRow(null)
  }

  fun addRow(row: ExecutableRow) {
    when (tableModel.rowCount) {
      0 -> {
        tableModel.addRow(row)
        tableModel.addRow(null)
      }
      1 -> tableModel.insertRow(0, row)
      else -> tableModel.insertRow(tableModel.rowCount - 1, row)
    }
  }

  fun removeRow(executable: Executable) {
    val rowToRemove = tableModel.items.firstOrNull { it?.executable == executable } ?: return
    removeRow(rowToRemove)
  }

  fun removeRow(row: ExecutableRow) {
    val index = tableModel.indexOf(row)
    if (index != -1) {
      tableModel.removeRow(index)
    }
  }

  fun replaceRow(oldRow: ExecutableRow, newRow: ExecutableRow) {
    val index = tableModel.indexOf(oldRow)
    if (index != -1) {
      tableModel.removeRow(index)
      tableModel.insertRow(index, newRow)
    }
  }
}