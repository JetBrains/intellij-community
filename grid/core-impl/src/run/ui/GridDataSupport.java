package com.intellij.database.run.ui;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.datagrid.mutating.CellMutation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface GridDataSupport {
  void revert(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns);
  boolean isDeletedRows(@NotNull ModelIndexSet<GridRow> rows);
  boolean isModified(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);
  boolean hasPendingChanges();
  boolean hasUnparsedValues();
  boolean hasMutator();
  boolean hasRowMutator();
  boolean canRevert();
  boolean isSubmitImmediately();
  void finishBuildingAndApply(@NotNull List<CellMutation.Builder> mutations);
  boolean isDeletedColumn(@NotNull ModelIndex<GridColumn> column);
  boolean isInsertedColumn(@NotNull ModelIndex<GridColumn> column);
  int getInsertedColumnsCount();
}
