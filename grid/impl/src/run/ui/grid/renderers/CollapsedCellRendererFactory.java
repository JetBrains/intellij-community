package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.extractors.ObjectFormatterConfig;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class CollapsedCellRendererFactory implements GridCellRendererFactory {
  private final DataGrid myGrid;
  private CollapsedCellRenderer myTextRenderer;

  public CollapsedCellRendererFactory(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  @Override
  public boolean supports(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    HierarchicalColumnsCollapseManager collapseManager =
      myGrid.getHierarchicalColumnsCollapseManager();
    return collapseManager != null && collapseManager.isColumnCollapsedSubtree(column);
  }

  @Override
  public @NotNull GridCellRenderer getOrCreateRenderer(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    if (myTextRenderer == null) {
      myTextRenderer = new CollapsedCellRenderer(myGrid);
      Disposer.register(myGrid, myTextRenderer);
    }
    return myTextRenderer;
  }

  @Override
  public void reinitSettings() {
    if (myTextRenderer == null) return;
    myTextRenderer.reinitSettings();
  }

  public static class CollapsedCellRenderer extends DefaultTextRendererFactory.TextRenderer {
    private static final char BULLET = '•';

    public CollapsedCellRenderer(@NotNull DataGrid grid) {
      super(grid, new JBEmptyBorder(0, 1, 0, 3));
    }

    @Override
    public int getSuitability(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      HierarchicalColumnsCollapseManager collapseManager =
        myGrid.getHierarchicalColumnsCollapseManager();
      return collapseManager != null && collapseManager.isColumnCollapsedSubtree(column) ? SUITABILITY_MIN + 1 : SUITABILITY_UNSUITABLE;
    }

    @Override
    protected @NotNull String getValueText(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Object value) {
      GridModel<GridRow, GridColumn> model = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
      GridColumn column = model.getColumn(columnIdx);

      if (model.getHierarchicalReader() == null || !(column instanceof HierarchicalGridColumn hierarchicalColumn)) {
        return "" + BULLET + BULLET + BULLET;
      }

      List<String> childrenNames =
        ContainerUtil.map(model.getHierarchicalReader().getSiblings(hierarchicalColumn), HierarchicalGridColumn::getName);

      return String.format("{ %s }", String.join(", ", childrenNames));
    }

    @Override
    protected @NotNull String getValueText(@NotNull ModelIndex<GridColumn> columnIdx,
                                           @Nullable ModelIndex<GridRow> rowIdx,
                                           @NotNull Object value) {
      GridModel<GridRow, GridColumn> model = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
      if (rowIdx == null) return getValueText(columnIdx, value);
      GridRow row = model.getRow(rowIdx);
      if (row == null) return getValueText(columnIdx, value);

      GridColumn column = model.getColumn(columnIdx);

      if (model.getHierarchicalReader() == null || !(column instanceof HierarchicalGridColumn hierarchicalColumn)) {
        return "" + BULLET + BULLET + BULLET;
      }

      List<String> childrenInfo =
        ContainerUtil.map(model.getHierarchicalReader().getSiblings(hierarchicalColumn), c -> {
          String name = c.getName();
          Object cellVal = c.getValue(row);

          return String.format("%s: %s", name, getShortenedValue(myGrid, c, ModelIndex.forColumn(myGrid, c.getColumnNumber()), cellVal));
        });

      return String.format("{ %s }", String.join(", ", childrenInfo));
    }
  }

  private static @NotNull String getShortenedValue(@NotNull DataGrid grid,
                                                   @NotNull GridColumn column,
                                                   @NotNull ModelIndex<GridColumn> columnIdx,
                                                   @Nullable Object value) {
    ObjectFormatterConfig formatterConfig = grid.getFormatterConfig(columnIdx);
    String valueString = formatterConfig == null
      ? Objects.requireNonNullElse(value, "null").toString()
      : Objects.requireNonNullElse(grid.getObjectFormatter().objectToString(value, column, formatterConfig), "null");

    int maxLength = 5;
    if (valueString.length() > maxLength) {
      return valueString.substring(0, maxLength) + "...";
    }

    return valueString;
  }
}
