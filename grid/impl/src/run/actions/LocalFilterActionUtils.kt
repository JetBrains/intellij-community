package com.intellij.database.run.actions

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridPresentationMode

object LocalFilterActionUtils {
  fun isGridInNonTransposedTableMode(grid: DataGrid) =
    grid.getPresentationMode() == GridPresentationMode.TABLE && !grid.getResultView().isTransposed()
}
