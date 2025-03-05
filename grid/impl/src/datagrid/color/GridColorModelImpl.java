package com.intellij.database.datagrid.color;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridMutator.DatabaseMutator;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class GridColorModelImpl implements GridColorModel {
  private ColorLayer[] myLayers;

  private final DataGrid myGrid;

  public GridColorModelImpl(@NotNull DataGrid grid,
                            @Nullable DatabaseMutator<GridRow, GridColumn> mutator,
                            boolean transparentRowHeaderBg,
                            boolean transparentColumnHeaderBg) {
    this(grid, createLayers(grid, mutator, transparentRowHeaderBg, transparentColumnHeaderBg));
  }

  public GridColorModelImpl(@NotNull DataGrid grid, ColorLayer @NotNull ... layers) {
    myGrid = grid;
    myLayers = layers;
    Arrays.sort(myLayers);
  }

  private static ColorLayer[] createLayers(@NotNull DataGrid grid,
                                           @Nullable DatabaseMutator<GridRow, GridColumn> mutator,
                                           boolean transparentRowHeaderBg,
                                           boolean transparentColumnHeaderBg) {
    return ContainerUtil.ar(
      new MarkupColorLayer(),
      new MutationsColorLayer(mutator),
      new HierarchicalAwareSelectionColorLayer(
        new SelectionColorLayer(grid, transparentRowHeaderBg, transparentColumnHeaderBg)
      ),
      new SearchSessionColorLayer()
    );
  }

  public @Nullable ColorLayer getLayer(@NotNull Class<? extends ColorLayer> clazz) {
    return ContainerUtil.find(myLayers, layer -> layer.getClass().isAssignableFrom(clazz));
  }

  public void removeLayer(@NotNull Class<? extends ColorLayer> clazz) {
    List<ColorLayer> layers = Arrays.asList(myLayers);
    layers.removeIf(layer -> layer.getClass().isAssignableFrom(clazz));
    myLayers = layers.toArray(ColorLayer[]::new);
  }

  public void removeLayer(@NotNull ColorLayer layer) {
    List<ColorLayer> layers = Arrays.asList(myLayers);
    layers.remove(layer);
    myLayers = layers.toArray(ColorLayer[]::new);
  }

  public void addLayer(@NotNull ColorLayer layer) {
    List<ColorLayer> layers = new ArrayList<>(Arrays.asList(myLayers));
    layers.add(layer);
    myLayers = layers.toArray(ColorLayer[]::new);
    Arrays.sort(myLayers);
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    Color color = null;
    for (ColorLayer layer : myLayers) {
      color = layer.getCellBackground(row, column, myGrid, color);
    }
    return color;
  }

  @Override
  public @Nullable Color getRowHeaderBackground(@NotNull ModelIndex<GridRow> row) {
    Color color = null;
    for (ColorLayer layer : myLayers) {
      color = layer.getRowHeaderBackground(row, myGrid, color);
    }
    return color;
  }

  @Override
  public @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column) {
    Color color = null;
    for (ColorLayer layer : myLayers) {
      color = layer.getColumnHeaderBackground(column, myGrid, color);
    }
    return color;
  }

  @Override
  public @Nullable Color getColumnHeaderBackground(@NotNull ModelIndex<GridColumn> column, int headerLine) {
    Color color = null;
    for (ColorLayer layer : myLayers) {
      color = layer.getColumnHeaderBackground(column, headerLine, myGrid, color);
    }
    return color;
  }

  @Override
  public @NotNull Color getRowHeaderForeground(@NotNull ModelIndex<GridRow> row) {
    Color color = null;
    for (ColorLayer layer : myLayers) {
      color = layer.getRowHeaderForeground(row, myGrid, color);
    }
    return Objects.requireNonNull(color);
  }

  @Override
  public @NotNull Color getColumnHeaderForeground(@NotNull ModelIndex<GridColumn> column) {
    Color color = null;
    for (ColorLayer layer : myLayers) {
      color = layer.getColumnHeaderForeground(column, myGrid, color);
    }
    return Objects.requireNonNull(color);
  }
}
