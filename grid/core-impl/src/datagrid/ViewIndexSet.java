package com.intellij.database.datagrid;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.datagrid.ViewIndex.*;

public abstract class ViewIndexSet<S> extends IndexSet<ViewIndex<S>> {
  public static <Row> ViewIndexSet<Row> forRows(CoreGrid<Row, ?> grid, int... rows) {
    return new ViewIndexSet<>(rows) {
      @Override
      public @NotNull ModelIndexSet<Row> toModel(@NotNull CoreGrid<?, ?> grid) {
        //noinspection unchecked
        return ModelIndexSet.forRows((CoreGrid<Row, ?>)grid, convert(row2Model(grid), asArray()));
      }

      @Override
      protected ViewIndex<Row> forValue(int value) {
        return forRow(null, value);
      }
    };
  }

  public static <Column> ViewIndexSet<Column> forColumns(CoreGrid<?, Column> grid, int... columns) {
    return new ViewIndexSet<>(columns) {
      @Override
      public @NotNull ModelIndexSet<Column> toModel(@NotNull CoreGrid<?, ?> grid) {
        //noinspection unchecked
        return ModelIndexSet.forColumns((CoreGrid<?, Column>)grid, convert(col2Model(grid), asArray()));
      }

      @Override
      protected ViewIndex<Column> forValue(int value) {
        return forColumn(null, value);
      }
    };
  }

  ViewIndexSet(int... indices) {
    super(indices);
  }

  public abstract @NotNull ModelIndexSet<S> toModel(@NotNull CoreGrid<?, ?> grid);

  @Override
  public String toString() {
    return "ViewIndexSet{" + StringUtil.join(values, ",") + "}";
  }
}
