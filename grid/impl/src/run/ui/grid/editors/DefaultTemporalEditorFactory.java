package com.intellij.database.run.ui.grid.editors;

import com.intellij.database.datagrid.DataGrid;
import org.jetbrains.annotations.NotNull;

public abstract class DefaultTemporalEditorFactory extends FormatBasedGridCellEditorFactory {
  @Override
  protected boolean makeFormatterLenient(@NotNull DataGrid grid) {
    return GridCellEditorHelper.get(grid).useLenientFormatterForTemporalObjects(grid);
  }
}
