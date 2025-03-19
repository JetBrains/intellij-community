package com.intellij.execution.multilaunch.design

import com.intellij.execution.multilaunch.design.columns.impl.ConditionColumn
import com.intellij.execution.multilaunch.design.columns.impl.ExecutableNameColumn
import com.intellij.execution.multilaunch.design.columns.impl.DisableDebuggingColumn
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExecutablesTableModel(
  viewModel: MultiLaunchConfigurationViewModel
) : ListTableModel<ExecutableRow?>(
  arrayOf(
    ExecutableNameColumn(viewModel),
    ConditionColumn(viewModel),
    DisableDebuggingColumn()
  ),
  viewModel.rows
) {
  override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
    if ((columnIndex != 0 && rowIndex == rowCount - 1)) {
      return false
    }
    return super.isCellEditable(rowIndex, columnIndex)
  }

  override fun canExchangeRows(oldIndex: Int, newIndex: Int): Boolean {
    // We can't touch the last row ("Add..." action)
    if (oldIndex == rowCount - 1 || newIndex == rowCount - 1) {
      return false
    }

    return true
  }
}