package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import org.jetbrains.annotations.NotNull;

public abstract class DefaultTemporalEditorFactory extends FormatBasedGridCellEditorFactory {
  @Override
  protected boolean makeFormatterLenient(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return GridCellEditorHelper.get(grid).useLenientFormatterForTemporalObjects(grid);
  }
}
