package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

public class GridCellRendererFactories {
  private static final Key<GridCellRendererFactories> RENDERER_FACTORIES_KEY = new Key<>("RENDERER_FACTORIES_KEY");

  private final List<GridCellRendererFactory> myDefaultFactories;

  public GridCellRendererFactories(@NotNull List<GridCellRendererFactory> factories) {
    myDefaultFactories = factories;
  }

  public void reinitSettings() {
    for (GridCellRendererFactory factory : myDefaultFactories) {
      factory.reinitSettings();
    }
  }

  public static void set(@NotNull DataGrid grid, @NotNull GridCellRendererFactories factories) {
    grid.putUserData(RENDERER_FACTORIES_KEY, factories);
  }

  public static @NotNull GridCellRendererFactories get(@NotNull DataGrid grid) {
    return Objects.requireNonNull(grid.getUserData(RENDERER_FACTORIES_KEY));
  }

  @Unmodifiable
  List<GridCellRendererFactory> getFactoriesFor(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return ContainerUtil.filter(myDefaultFactories, factory -> factory.supports(row, column));
  }
}
