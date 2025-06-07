package com.intellij.database.run.ui.grid.renderers;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ColumnNamesHierarchyNode;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.EditorTextFieldCellRenderer.AbbreviatingRendererComponent;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class NestedTableCellRendererFactory implements GridCellRendererFactory {
  private final DataGrid myGrid;

  NestedTableTextRenderer textRenderer;

  public NestedTableCellRendererFactory(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  @Override
  public boolean supports(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    Object cellValue = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column);
    return cellValue instanceof NestedTable;
  }

  @Override
  public @NotNull GridCellRenderer getOrCreateRenderer(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    if (textRenderer == null) {
      textRenderer = new NestedTableTextRenderer(myGrid);
      Disposer.register(myGrid, textRenderer);
    }

    return textRenderer;
  }

  public static class NestedTableTextRenderer extends DefaultTextRendererFactory.TextRenderer {
    public NestedTableTextRenderer(@NotNull DataGrid grid) {
      super(grid, new JBEmptyBorder(0, 1, 0, 3));
    }

    @Override
    public @NotNull JComponent getComponent(@NotNull ViewIndex<GridRow> rowIdx,
                                            @NotNull ViewIndex<GridColumn> columnIdx,
                                            @Nullable Object value,
                                            @NotNull ModelIndex<GridColumn> modelColumn) {
      AbbreviatingRendererComponent component =
        (AbbreviatingRendererComponent) super.getComponent(rowIdx, columnIdx, value, modelColumn);

      if (!(myGrid.getResultView() instanceof JTable tableView)) {
        return component;
      }

      int hoveredRowIdx = TableHoverListener.getHoveredRow(tableView);
      boolean isHoveredRow = (hoveredRowIdx == rowIdx.asInteger());
      component.setText(getValueText(value, isHoveredRow));

      return component;
    }

    private static String getValueText(@Nullable Object value, boolean isHovered) {
      if (value == null) return "";

      NestedTable nestedTable = (NestedTable) value;
      List<ColumnNamesHierarchyNode> columns = nestedTable.getColumnsHierarchy().getChildren();

      String valueText = columns.isEmpty() || nestedTable.getRowsNum() == 0
                         ? "[empty]"
                         : String.format("[%d rows x %d columns]", nestedTable.getTotalRowsNum(), columns.size());

      return isHovered ? valueText + "➘" : valueText + " ";
    }


    @Override
    protected @NotNull String getValueText(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Object value) {
      return "";
    }

    @Override
    public int getSuitability(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      Object cellValue = myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column);
      return cellValue instanceof NestedTable ? SUITABILITY_MIN+1 : SUITABILITY_UNSUITABLE;
    }
  }
}