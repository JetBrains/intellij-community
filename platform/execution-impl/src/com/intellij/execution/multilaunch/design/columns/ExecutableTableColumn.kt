package com.intellij.execution.multilaunch.design.columns

import com.intellij.execution.multilaunch.design.ExecutableRow
import com.intellij.util.ui.ColumnInfo

abstract class ExecutableTableColumn<TValue>(name: String) : ColumnInfo<ExecutableRow, TValue>(name) {
  override fun isCellEditable(item: ExecutableRow?) = true
}
