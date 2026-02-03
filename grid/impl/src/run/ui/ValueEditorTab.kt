package com.intellij.database.run.ui

import com.intellij.database.datagrid.DataGrid
import com.intellij.openapi.extensions.ExtensionPointName

interface ValueEditorTab {
  companion object {
    val EP = ExtensionPointName.create<ValueEditorTab>("com.intellij.database.datagrid.valueEditorTab")
  }

  val priority: Int

  fun createTabInfoProvider(grid: DataGrid, openValueEditorTab: () -> Unit): TabInfoProvider

  fun applicable(grid: DataGrid): Boolean = true
}