package com.intellij.database.datagrid;

import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.datagrid.ModelIndex.*;

public abstract class ModelIndexSet<S> extends IndexSet<ModelIndex<S>> {

  public static <Row> ModelIndexSet<Row> forRows(CoreGrid<Row, ?> grid, int... rows) {
    return forRows(grid.getDataModel(DataAccessType.DATABASE_DATA), rows);
  }

  public static <Row> ModelIndexSet<Row> forRows(GridModel<Row, ?> model, int... rows) {
    return new Rows<>(rows);
  }

  public static <Row> ModelIndexSet<Row> forRows(GridModel<Row, ?> model, ModelIndex<Row>... rows) {
    int[] rowsIdx = ArrayUtil.toIntArray(ContainerUtil.map(rows, row -> row.asInteger()));
    return new Rows<>(rowsIdx);
  }

  public static <Row> ModelIndexSet<Row> forRows(CoreGrid<Row, ?> grid, Iterable<ModelIndex<Row>> rows) {
    return forRows(grid.getDataModel(DataAccessType.DATABASE_DATA), rows);
  }

  public static <Row> ModelIndexSet<Row> forRows(GridModel<Row, ?> model, Iterable<ModelIndex<Row>> rows) {
    int[] rowsIdx = ArrayUtil.toIntArray(ContainerUtil.map(rows, row -> row.asInteger()));
    return new Rows<>(rowsIdx);
  }

  public static <Column> ModelIndexSet<Column> forColumns(CoreGrid<?, Column> grid, int... columns) {
    return forColumns(grid.getDataModel(DataAccessType.DATABASE_DATA), columns);
  }

  public static <Column> ModelIndexSet<Column> forColumns(GridModel<?, Column> model, int... columns) {
    return new Columns<>(columns);
  }

  public static <Column> ModelIndexSet<Column> forColumns(GridModel<?, Column> model, ModelIndex<Column>... columns) {
    int[] columnsIdx = ArrayUtil.toIntArray(ContainerUtil.map(columns, column -> column.asInteger()));
    return new Columns<>(columnsIdx);
  }

  public static <Column> ModelIndexSet<Column> forColumns(CoreGrid<?, Column> grid, Iterable<ModelIndex<Column>> columns) {
    return forColumns(grid.getDataModel(DataAccessType.DATABASE_DATA), columns);
  }

  public static <Column> ModelIndexSet<Column> forColumns(GridModel<?, Column> model, Iterable<ModelIndex<Column>> columns) {
    int[] columnsIdx = ArrayUtil.toIntArray(ContainerUtil.map(columns, column -> column.asInteger()));
    return new Columns<>(columnsIdx);
  }

  private ModelIndexSet(int... indices) {
    super(indices);
  }

  public abstract @NotNull ViewIndexSet<S> toView(@NotNull CoreGrid<?, ?> grid);

  @Override
  public String toString() {
    return "Model" + getClass().getSimpleName() + "{" + StringUtil.join(values, ",") + "}";
  }

  private static class Rows<Row> extends ModelIndexSet<Row> {
    Rows(int... indices) {
      super(indices);
    }

    @Override
    public @NotNull ViewIndexSet<Row> toView(@NotNull CoreGrid<?, ?> grid) {
      //noinspection unchecked
      return ViewIndexSet.forRows((CoreGrid<Row, ?>)grid, IndexSet.convert(row2View(grid), asArray()));
    }

    @Override
    protected ModelIndex<Row> forValue(int value) {
      return forRow((GridModel<Row, ?>)null, value);
    }
  }

  private static class Columns<Column> extends ModelIndexSet<Column> {
    Columns(int... indices) {
      super(indices);
    }

    @Override
    public @NotNull ViewIndexSet<Column> toView(@NotNull CoreGrid<?, ?> grid) {
      //noinspection unchecked
      return ViewIndexSet.forColumns((CoreGrid<?, Column>)grid, IndexSet.convert(col2View(grid), asArray()));
    }

    @Override
    protected ModelIndex<Column> forValue(int value) {
      return forColumn((GridModel<?, Column>)null, value);
    }
  }
}
