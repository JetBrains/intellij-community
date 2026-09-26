package com.intellij.database.datagrid;


import com.intellij.database.run.ui.DataAccessType;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntUnaryOperator;

public abstract class ModelIndex<S> extends Index {

  public static <RowType> ModelIndex<RowType> forRow(CoreGrid<RowType, ?> grid, int row) {
    return forRow(grid.getDataModel(DataAccessType.DATABASE_DATA), row);
  }

  public static <RowType> ModelIndex<RowType> forRow(GridModel<RowType, ?> model, int row) {
    return new Row<>(row);
  }

  public static <ColumnType> ModelIndex<ColumnType> forColumn(CoreGrid<?, ColumnType> grid, int column) {
    return forColumn(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS), column);
  }

  public static <ColumnType> ModelIndex<ColumnType> forColumn(GridModel<?, ColumnType> model, int column) {
    return new Column<>(column);
  }

  private ModelIndex(int value) {
    super(value);
  }

  public abstract @NotNull ViewIndex<S> toView(@NotNull CoreGrid<?, ?> grid);

  public abstract boolean isValid(@NotNull GridModel<?, ?> model);

  public boolean isValid(@NotNull CoreGrid<?, ?> grid) {
    return isValid(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS));
  }

  @Override
  public String toString() {
    return "Model" + getClass().getSimpleName() + "{" + value + "}";
  }


  static IntUnaryOperator row2View(CoreGrid<?, ?> grid) {
    return grid.getRawIndexConverter().row2View();
  }

  static IntUnaryOperator col2View(CoreGrid<?, ?> grid) {
    return grid.getRawIndexConverter().column2View();
  }


  private static class Column<ColumnType> extends ModelIndex<ColumnType> {
    Column(int value) {
      super(value);
    }

    @Override
    public @NotNull ViewIndex<ColumnType> toView(@NotNull CoreGrid<?, ?> grid) {
      //noinspection unchecked
      return ViewIndex.forColumn((CoreGrid<?, ColumnType>)grid, col2View(grid).applyAsInt(value));
    }

    @Override
    public boolean isValid(@NotNull GridModel<?, ?> model) {
      //noinspection unchecked
      return ((GridModel<?, ColumnType>)model).isValidColumnIdx(this);
    }
  }

  private static class Row<RowType> extends ModelIndex<RowType> {
    Row(int value) {
      super(value);
    }

    @Override
    public @NotNull ViewIndex<RowType> toView(@NotNull CoreGrid<?, ?> grid) {
      //noinspection unchecked
      return ViewIndex.forRow((CoreGrid<RowType, ?>)grid, row2View(grid).applyAsInt(value));
    }

    @Override
    public boolean isValid(@NotNull GridModel<?, ?> model) {
      //noinspection unchecked
      return ((GridModel<RowType, ?>)model).isValidRowIdx(this);
    }
  }
}
