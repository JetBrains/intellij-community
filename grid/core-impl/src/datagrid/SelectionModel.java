package com.intellij.database.datagrid;

import com.intellij.database.run.ui.grid.selection.GridSelectionTracker;
import org.jetbrains.annotations.NotNull;

public interface SelectionModel<Row, Column> {

  @NotNull GridSelection<Row, Column> store();

  void restore(@NotNull GridSelection<Row, Column> selection);

  @NotNull
  GridSelection<Row, Column> fit(@NotNull GridSelection<Row, Column> selection);

  @NotNull GridSelectionTracker getTracker();

  void setSelection(@NotNull ModelIndexSet<Row> rows, @NotNull ModelIndexSet<Column> columns);

  void setSelection(@NotNull ModelIndex<Row> row, @NotNull ModelIndex<Column> column);

  void setRowSelection(@NotNull ModelIndexSet<Row> selection, boolean selectAtLeastOneCell);

  void setRowSelection(@NotNull ModelIndex<Row> selection, boolean selectAtLeastOneCell);

  void addRowSelection(@NotNull ModelIndexSet<Row> selection);

  void setColumnSelection(@NotNull ModelIndexSet<Column> selection, boolean selectAtLeastOneCell);

  void setColumnSelection(@NotNull ModelIndex<Column> selection, boolean selectAtLeastOneCell);

  boolean isSelectionEmpty();

  boolean isSelected(@NotNull ModelIndex<Row> row, @NotNull ModelIndex<Column> column);

  boolean isSelected(@NotNull ViewIndex<Row> row, @NotNull ViewIndex<Column> column);

  boolean isSelectedColumn(@NotNull ModelIndex<Column> column);

  boolean isSelectedRow(@NotNull ModelIndex<Row> row);

  int getSelectedRowCount();

  int getSelectedColumnCount();

  void selectWholeRow();

  void selectWholeColumn();

  void clearSelection();

  @NotNull
  ModelIndex<Row> getSelectedRow();

  default @NotNull ModelIndex<Row> getLeadSelectionRow() {
    return getSelectedRow();
  }

  @NotNull
  ModelIndexSet<Row> getSelectedRows();

  @NotNull
  ModelIndex<Column> getSelectedColumn();

  default @NotNull ModelIndex<Column> getLeadSelectionColumn() {
    return getSelectedColumn();
  }

  @NotNull
  ModelIndexSet<Column> getSelectedColumns();
}
