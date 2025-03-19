package com.intellij.database.run.actions

import com.intellij.database.datagrid.*

object LocalFilterActionUtils {
  fun isGridInNonTransposedTableMode(grid: DataGrid) =
    grid.getPresentationMode() == GridPresentationMode.TABLE && !grid.getResultView().isTransposed()
}
