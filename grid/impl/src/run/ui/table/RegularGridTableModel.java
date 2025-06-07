package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.extractors.BinaryDisplayType;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.Range;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.database.run.actions.ChangeColumnDisplayTypeAction.isBinary;


public class RegularGridTableModel extends GridTableModel {
  public RegularGridTableModel(@NotNull DataGrid grid) {
    super(grid);
  }

  @Override
  protected int row(int rowIndex, int columnIndex) {
    return rowIndex;
  }

  @Override
  protected int col(int rowIndex, int columnIndex) {
    return columnIndex;
  }

  @Override
  public @NotNull TableResultViewColumn createColumn(int columnDataIdx) {
    ModelIndex<GridColumn> columnIdx = ModelIndex.forColumn(myGrid, columnDataIdx);
    GridColumn c = Objects.requireNonNull(myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(columnIdx));
    return new RegularTableColumn(c, columnDataIdx);
  }


  @Override
  protected Range<Integer> rowRange(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns) {
    return getSmallestCoveringRange(rows);
  }


  @Override
  public int getRowCount() {
    return myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRowCount();
  }

  @Override
  public int getColumnCount() {
    return myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnCount();
  }


  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columns) {
    fireTableStructureChanged();
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    fireTableStructureChanged();
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    Range<Integer> rowRange = getSmallestCoveringRange(rows);
    fireTableRowsInserted(rowRange.getFrom(), rowRange.getTo());
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    Range<Integer> rowRange = getSmallestCoveringRange(rows);
    fireTableRowsDeleted(rowRange.getFrom(), rowRange.getTo());
  }

  protected class RegularTableColumn extends TableResultViewColumn {
    private static final int HIERARCHICAL_COLUMN_ADDITIONAL_WIDTH = 40;
    private final GridColumn myColumn;
    private Icon myIconCache;

    private RegularTableColumn(@NotNull GridColumn column, int modelIndex) {
      super(modelIndex);
      myColumn = column;
    }

    @Override
    public Icon getIcon(boolean forDisplay) {
      if (myIconCache != null) return myIconCache;
      Icon icon = GridHelper.get(myGrid).getColumnIcon(myGrid, myColumn, forDisplay);
      if (forDisplay) myIconCache = icon;
      return icon;
    }

    @Override
    public @NlsContexts.ColumnName @NotNull String getHeaderValue() {
      String result = myGrid.getName(myColumn);
      String displayTypeName;
      ModelIndex<GridColumn> columnIdx = ModelIndex.forColumn(myGrid, myColumn.getColumnNumber());
      if (isBinary(columnIdx, myGrid)) {
        DisplayType displayType = myGrid.getDisplayType(columnIdx);
        DisplayType pureDisplayType = myGrid.getPureDisplayType(columnIdx);
        displayTypeName = pureDisplayType == BinaryDisplayType.DETECT && displayType == BinaryDisplayType.HEX ? "" : " (" + displayType.getName() + ")";
      }
      else {
        displayTypeName = "";
      }
      return result + displayTypeName;
    }

    @Override
    public int getAdditionalWidth() {
      if (myColumn instanceof HierarchicalGridColumn hierarchicalColumn
          && hierarchicalColumn.isLeftMostChildOfDirectAncestor()) {
        return HIERARCHICAL_COLUMN_ADDITIONAL_WIDTH;
      }
      return ADDITIONAL_COLUMN_WIDTH;
    }

    @Override
    public @NlsContexts.ColumnName @NotNull List<String> getMultilineHeaderValues() {
      List<String> result = new ArrayList<>();
      result.add(getHeaderValue());

      if (myColumn instanceof HierarchicalGridColumn hierarchicalColumn) {
        boolean wasPreviousNodeLeftMost = hierarchicalColumn.isLeftMostChildOfDirectAncestor();
        for (HierarchicalGridColumn current = hierarchicalColumn.getParent(); current != null; current = current.getParent()) {
          result.add(wasPreviousNodeLeftMost ? current.getName() : "    ");
          wasPreviousNodeLeftMost &= current.isLeftMostChildOfDirectAncestor();
        }
      }

      return ContainerUtil.reverse(result);
    }

    @Override
    public @NlsContexts.Tooltip @Nullable String getTooltipText() {
      return GridHelper.get(myGrid).getColumnTooltipHtml(myGrid, ModelIndex.forColumn(myGrid, myColumn.getColumnNumber()));
    }
  }
}
