package com.intellij.database.run.ui;

import com.intellij.database.datagrid.CoreGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.run.ui.grid.editors.GridCellEditorHelper;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface GridEditGuard {

  @NlsContexts.HintText
  @NotNull
  String getReasonText(@NotNull CoreGrid<GridRow, GridColumn> grid);

  boolean rejectEdit(@NotNull CoreGrid<GridRow, GridColumn> grid);

  static @Nullable GridEditGuard get(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    Set<GridEditGuard> guards = GridCellEditorHelper.get(grid).getEditGuards();
    if (guards == null) return null;
    for (GridEditGuard guard : guards) {
      if (guard.rejectEdit(grid)) return guard;
    }
    return null;
  }
}
