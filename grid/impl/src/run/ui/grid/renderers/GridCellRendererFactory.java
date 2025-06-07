package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import org.jetbrains.annotations.NotNull;

public interface GridCellRendererFactory {
  boolean supports(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);

  @NotNull
  GridCellRenderer getOrCreateRenderer(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);

  default void reinitSettings() {
  }
}
