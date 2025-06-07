package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface GridModelUpdater {
  void removeRows(int firstRowIndex, int rowCount);

  void setColumns(@NotNull List<? extends GridColumn> columns);

  void setRows(int firstRowIndex, @NotNull List<? extends GridRow> rows, @NotNull GridRequestSource source);

  void addRows(List<? extends GridRow> rows);

  void afterLastRowAdded();
}
