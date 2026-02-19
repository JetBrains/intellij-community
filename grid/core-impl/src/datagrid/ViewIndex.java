package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

import java.util.function.IntUnaryOperator;

public abstract class ViewIndex<S> extends Index {

  static IntUnaryOperator row2Model(CoreGrid<?, ?> grid) {
    return grid.getRawIndexConverter().row2Model();
  }

  static IntUnaryOperator col2Model(CoreGrid<?, ?> grid) {
    return grid.getRawIndexConverter().column2Model();
  }


  public static <Row> ViewIndex<Row> forRow(CoreGrid<Row, ?> grid, int row) {
    return new ViewIndex<>(row) {
      @Override
      public @NotNull ModelIndex<Row> toModel(@NotNull CoreGrid<?, ?> grid) {
        //noinspection unchecked
        return ModelIndex.forRow((CoreGrid<Row, ?>)grid, row2Model(grid).applyAsInt(value));
      }

      @Override
      public boolean isValid(@NotNull CoreGrid<?, ?> grid) {
        return grid.getRawIndexConverter().isValidViewRowIdx(asInteger());
      }
    };
  }

  public static <Column> ViewIndex<Column> forColumn(CoreGrid<?, Column> grid, int column) {
    return new ViewIndex<>(column) {
      @Override
      public @NotNull ModelIndex<Column> toModel(@NotNull CoreGrid<?, ?> grid) {
        //noinspection unchecked
        return ModelIndex.forColumn((CoreGrid<?, Column>)grid, col2Model(grid).applyAsInt(value));
      }

      @Override
      public boolean isValid(@NotNull CoreGrid<?, ?> grid) {
        return grid.getRawIndexConverter().isValidViewColumnIdx(asInteger());
      }
    };
  }


  ViewIndex(int value) {
    super(value);
  }

  public abstract ModelIndex<S> toModel(@NotNull CoreGrid<?, ?> grid);

  public abstract boolean isValid(@NotNull CoreGrid<?, ?> grid);

  @Override
  public String toString() {
    return "ViewIndex{" + value + "}";
  }
}
