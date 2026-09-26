package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.GridCellRequest;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import org.jetbrains.annotations.NotNull;

public interface GridCellRendererFactory {
  boolean supports(@NotNull GridCellRequest<GridRow, GridColumn> request);

  @NotNull
  GridCellRenderer getOrCreateRenderer(@NotNull GridCellRequest<GridRow, GridColumn> request);

  default void reinitSettings() {
  }
}
