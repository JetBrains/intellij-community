package com.intellij.database.editor

import com.intellij.database.datagrid.DataGrid
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Creates the Structure View model of a data grid.
 *
 * The `intellij.grid.structureview` module holds the implementation.
 */
interface GridStructureViewModelFactory {
  fun createModel(project: Project, grid: DataGrid): StructureViewModel

  companion object {
    private val EP_NAME: ExtensionPointName<GridStructureViewModelFactory> =
      ExtensionPointName("com.intellij.database.datagrid.structureViewModelFactory")

    /**
     * Returns the Structure View builder of the grid.
     * Returns null when no factory is registered.
     */
    @JvmStatic
    fun createBuilder(project: Project, grid: DataGrid): StructureViewBuilder? {
      val factory = EP_NAME.extensionList.firstOrNull() ?: return null
      return object : TreeBasedStructureViewBuilder() {
        override fun isRootNodeShown(): Boolean = false

        override fun createStructureViewModel(editor: Editor?): StructureViewModel = factory.createModel(project, grid)
      }
    }
  }
}
