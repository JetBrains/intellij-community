package com.intellij.database.datagrid.mutating;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;


public class CellMutation extends Mutation {
  private final ModelIndex<GridColumn> myColumn;
  private final Object myNewValue;

  public CellMutation(@NotNull ModelIndex<GridRow> row,
                      @NotNull ModelIndex<GridColumn> column,
                      @Nullable Object newValue) {
    super(row);
    myColumn = column;
    myNewValue = newValue;
  }

  public boolean canMergeByRowWith(@NotNull CellMutation mutation) {
    return mutation.getRow().equals(getRow());
  }

  public @Nullable Object getValue() {
    return myNewValue;
  }

  public @NotNull ModelIndex<GridColumn> getColumn() {
    return myColumn;
  }

  public @Nullable RowMutation createRowMutation(@NotNull GridModel<GridRow, GridColumn> model) {
    GridColumn column = model.getColumn(myColumn);
    GridRow row = model.getRow(getRow());
    return row == null || column == null ?
           null :
           new RowMutation(row, Collections.singletonList(new ColumnQueryData(column, myNewValue)));
  }

  public static class Builder {
    private ModelIndex<GridRow> myRow;
    private ModelIndex<GridColumn> myColumn;
    private Object myValue;

    public @NotNull Builder row(@NotNull ModelIndex<GridRow> row) {
      myRow = row;
      return this;
    }

    public @NotNull Builder column(@NotNull ModelIndex<GridColumn> column) {
      myColumn = column;
      return this;
    }

    public @NotNull Builder value(@Nullable Object value) {
      myValue = value;
      return this;
    }

    public @Nullable ModelIndex<GridRow> getRow() {
      return myRow;
    }

    public @NotNull CellMutation build() {
      return new CellMutation(myRow, myColumn, myValue);
    }
  }
}
