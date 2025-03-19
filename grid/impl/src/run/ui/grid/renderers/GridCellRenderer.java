package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.*;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public abstract class GridCellRenderer implements Disposable {
  public static final int SUITABILITY_UNSUITABLE = 0;
  public static final int SUITABILITY_MIN = 1;
  public static final int SUITABILITY_MAX = 10;

  public final DataGrid myGrid;

  protected GridCellRenderer(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  public abstract int getSuitability(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);

  public abstract @NotNull JComponent getComponent(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, @Nullable Object value);

  @Override
  public void dispose() {
  }

  public void clearCache() {
  }

  public abstract void reinitSettings();

  public static @NotNull GridCellRenderer getRenderer(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    GridCellRendererFactories factories = GridCellRendererFactories.get(grid);

    GridCellRenderer bestRenderer = null;
    int bestSuitability = SUITABILITY_UNSUITABLE;

    for (GridCellRendererFactory factory : factories.getFactoriesFor(row, column)) {
      GridCellRenderer renderer = factory.getOrCreateRenderer(row, column);
      int suitability = renderer.getSuitability(row, column);
      if (suitability > bestSuitability) {
        bestRenderer = renderer;
        bestSuitability = suitability;
      }
    }

    return Objects.requireNonNull(bestRenderer);
  }
}
