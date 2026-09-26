package com.intellij.grid.structureview

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.editor.GridStructureViewModelFactory
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.openapi.project.Project

internal class TableEditorStructureViewModelFactory : GridStructureViewModelFactory {
  override fun createModel(project: Project, grid: DataGrid): StructureViewModel = TableEditorStructureViewModel(project, grid)
}
