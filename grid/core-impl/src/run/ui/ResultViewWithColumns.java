package com.intellij.database.run.ui;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ResultViewColumn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Liudmila Kornilova
 **/
public interface ResultViewWithColumns {
  void changeSelectedColumnsWidth(int delta);
  void fitColumnsToViewport();
  void resetColumnWidths();
  void createDefaultColumnsFromModel();
  /**
   * {@code column} is an index in the view's column-data axis: a row index when the view is transposed,
   * a column index otherwise. Use {@link #getLayoutColumnForDataColumn} when the index is always a data column.
   */
  @Nullable ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column);

  /**
   * Layout column backing the given data column, or null when there is none - in particular for a transposed view,
   * where layout columns are backed by rows.
   */
  default @Nullable ResultViewColumn getLayoutColumnForDataColumn(@NotNull ModelIndex<GridColumn> column) {
    return getLayoutColumn(column);
  }
}
