package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.table.TableResultView;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.List;
import java.util.*;


public class DefaultGridColumnLayout implements GridColumnLayout<GridRow, GridColumn> {
  private static final Key<Integer> MAX_CELL_WIDTH_KEY = new Key<>("MaxCellWidth");

  private static final double GOLD = 0.5 * (3 - Math.sqrt(5));
  private static final int MIN_COLUMN_WIDTH = 40;

  private static final int FIRST_ROWS_FOR_SIZE_CALCULATION = 100;
  private static final int FIRST_ROWS_FOR_SIZE_CALCULATION_LOTS_OF_COLUMNS = 5;
  private static final int MAX_ROW_IDX_FOR_SIZE_CALCULATION = 10000;
  private static final int MAX_ROW_IDX_FOR_SIZE_CALCULATION_LOTS_OF_COLUMNS = 10;
  private static final int FIRST_ROWS_FOR_SIZE_CALCULATION_TRANSPOSED = 10;
  private static final int MAX_ROW_IDX_FOR_SIZE_CALCULATION_TRANSPOSED = 10;

  private final TableResultView myResultView;
  private final DataGrid myGrid;
  private boolean myInvalidateWidestCellValues;

  public DefaultGridColumnLayout(@NotNull TableResultView resultView, @NotNull DataGrid grid) {
    myResultView = resultView;
    myGrid = grid;
  }

  @Override
  public boolean resetLayout() {
    return doLayout((myResultView.isTransposed() ? myGrid.getVisibleRows() : myGrid.getVisibleColumns()).asList());
  }

  @Override
  public void newColumnsAdded(ModelIndexSet<GridColumn> columnIndices) {
    updateWidestCellValueCaches(myGrid.getVisibleRows(), columnIndices);
  }

  @Override
  public void newRowsAdded(ModelIndexSet<GridRow> rowIndices) {
    updateWidestCellValueCaches(rowIndices, myGrid.getVisibleColumns());
  }

  @Override
  public void columnsShown(ModelIndexSet<?> columnDataIndices) {
    doLayout(columnDataIndices.asList());
  }

  @Override
  public void invalidateCache() {
    myInvalidateWidestCellValues = true;
  }

  protected boolean doLayout(List<? extends ModelIndex<?>> columnDataIndices) {
    class LayoutInfo {
      int min;
      int preferred;
      int full;
    }

    if (myInvalidateWidestCellValues) {
      updateWidestCellValueCaches(myGrid.getVisibleRows(), myGrid.getVisibleColumns());
    }

    int allColumnsPreferredWidth = 0;
    int extraWidth = 0; // extra width for empty/light columns
    int allColumnsFullWidth = 0;
    Map<ResultViewColumn, LayoutInfo> columnWidths = new HashMap<>();
    Set<? extends ModelIndex<?>> columnsToResize = new HashSet<>(columnDataIndices);
    List<? extends ModelIndex<?>> visibleColumnDataIndices = (myResultView.isTransposed() ? myGrid.getVisibleRows() : myGrid.getVisibleColumns()).asList();

    JComponent gridPanel = myGrid.getPanel().getComponent();
    JScrollPane scrollPane = UIUtil.findComponentOfType(gridPanel, JScrollPane.class);
    int availableWidth = scrollPane != null ? scrollPane.getViewportBorderBounds().width : gridPanel.getWidth();
    if (availableWidth == 0) return false;
    availableWidth = Math.max(availableWidth, 400);

    for (ModelIndex<?> columnDataIdx : visibleColumnDataIndices) {
      ResultViewColumn column = myResultView.getLayoutColumn(columnDataIdx);
      if (column == null) continue; // will never happen
      if (columnsToResize.contains(columnDataIdx)) {
        LayoutInfo layoutInfo = new LayoutInfo();
        layoutInfo.min = Math.max(MIN_COLUMN_WIDTH, computeHeaderWidth(column));
        layoutInfo.full = Math.max(layoutInfo.min, computeColumnWidth(column));
        layoutInfo.preferred = Math.min(layoutInfo.full, (int)(availableWidth * GOLD));

        columnWidths.put(column, layoutInfo);

        allColumnsPreferredWidth += layoutInfo.preferred;
        extraWidth += layoutInfo.min == layoutInfo.full ? layoutInfo.preferred : 0;
        allColumnsFullWidth += layoutInfo.full;
      }
      else {
        int currentWidth = column.getColumnWidth();
        allColumnsPreferredWidth += currentWidth;
        extraWidth += currentWidth;
        allColumnsFullWidth += currentWidth;
      }
    }

    // If we insert a row in transposed mode, the added column is too narrow
    // as the column has no content and a header with a row number in it.
    // It makes sense to make it at least as wide as the 'average' column in the table.
    if (columnDataIndices.size() == 1 && visibleColumnDataIndices.size() > 1) {
      ModelIndex<?> insertedIdx = columnDataIndices.get(0);
      boolean insertedIdxIsValid = myResultView.isTransposed() && GridUtil.isInsertedRow(myGrid, cast(insertedIdx));
      ResultViewColumn insertedColumn = insertedIdxIsValid ? myResultView.getLayoutColumn(insertedIdx) : null;
      if (insertedColumn != null) {
        LayoutInfo layoutInfo = columnWidths.get(insertedColumn);
        int averageColumnWidth = (allColumnsPreferredWidth - layoutInfo.preferred) / (visibleColumnDataIndices.size() - 1);
        if (averageColumnWidth > layoutInfo.preferred) {
          extraWidth -= layoutInfo.preferred;
          allColumnsPreferredWidth -= layoutInfo.preferred;
          allColumnsFullWidth -= layoutInfo.full;

          layoutInfo.min = Math.min(averageColumnWidth, layoutInfo.min);
          layoutInfo.preferred = averageColumnWidth;
          layoutInfo.full = averageColumnWidth;

          extraWidth += layoutInfo.preferred;
          allColumnsPreferredWidth += layoutInfo.preferred;
          allColumnsFullWidth += layoutInfo.full;
        }
      }
    }


    int requiredWidth = availableWidth >= allColumnsFullWidth ? allColumnsFullWidth : Math.max(availableWidth, allColumnsPreferredWidth);
    int requiredPlusExtra = requiredWidth + extraWidth;
    int widthToDistribute = visibleColumnDataIndices.isEmpty() || availableWidth <= requiredWidth ? 0 :
                            (int)((requiredPlusExtra < GOLD * availableWidth ? GOLD * availableWidth :
                                   requiredPlusExtra < (1 - GOLD) * availableWidth ? (1 - GOLD) * availableWidth :
                                   availableWidth) - allColumnsPreferredWidth);

    boolean oneColumn = columnWidths.size() == 1;
    for (ResultViewColumn column : columnWidths.keySet()) {
      LayoutInfo layoutInfo = columnWidths.get(column);
      if (oneColumn) {
        column.setColumnWidth(Math.min(availableWidth, layoutInfo.full));
      }
      else {
        int expandedWidth = layoutInfo.preferred + (int)(widthToDistribute * layoutInfo.full / (double)allColumnsFullWidth);
        column.setColumnWidth(Math.min(availableWidth, Math.min(expandedWidth, (int)(layoutInfo.full * 1.2))));
      }
    }
    return true;
  }

  private static int computeColumnWidth(ResultViewColumn column) {
    return ObjectUtils.notNull(column.getUserData(MAX_CELL_WIDTH_KEY), 0) + column.getAdditionalWidth();
  }

  private int computeHeaderWidth(ResultViewColumn column) {
    var tableHeader = myResultView.getTableHeader();
    var cellRendererPane = new CellRendererPane();
    tableHeader.add(cellRendererPane);
    TableCellRenderer cellRenderer = tableHeader.getDefaultRenderer();
    TableResultView.MyCellRenderer renderer = ObjectUtils.tryCast(cellRenderer, TableResultView.MyCellRenderer.class);
    Component headerComponent;
    if (renderer != null) {
      headerComponent = renderer.getHeaderCellRendererComponent(column.getModelIndex(), false);
    }
    else {
      int viewIndex = myResultView.convertColumnIndexToView(column.getModelIndex());
      headerComponent = cellRenderer.getTableCellRendererComponent(myResultView, column.getHeaderValue(), false, false, -1, viewIndex);
    }
    cellRendererPane.add(headerComponent);
    headerComponent.validate();
    var res = headerComponent.getPreferredSize().width + column.getAdditionalWidth();
    tableHeader.remove(cellRendererPane);
    return res;
  }

  private void updateWidestCellValueCaches(ModelIndexSet<GridRow> rowIndices, ModelIndexSet<GridColumn> columnIndices) {
    JBIterable<? extends ViewIndex<?>> viewRowIndices = (myResultView.isTransposed() ? columnIndices : rowIndices).toView(myGrid).asIterable();
    JBIterable<? extends ModelIndex<?>> columnDataIndices = (myResultView.isTransposed() ? rowIndices : columnIndices).asIterable();
    List<ModelIndex<?>> toLayOut = new ArrayList<>();

    int columnNum = 0;
    for (ModelIndex<?> columnDataIdx : columnDataIndices) {
      ViewIndex<?> viewColumnIdx = columnDataIdx.toView(myGrid);
      ResultViewColumn layoutColumn = myResultView.getLayoutColumn(columnDataIdx, viewColumnIdx);
      if (layoutColumn == null) continue;

      Integer cachedMaxCellWidth = myInvalidateWidestCellValues ? null : layoutColumn.getUserData(MAX_CELL_WIDTH_KEY);
      int maxCellWidth = getMaximumPreferredWidth(viewRowIndices, viewColumnIdx, columnNum++);
      if (!myInvalidateWidestCellValues && cachedMaxCellWidth == null) {
        toLayOut.add(columnDataIdx);
      }
      if (cachedMaxCellWidth == null || maxCellWidth > cachedMaxCellWidth) {
        layoutColumn.putUserData(MAX_CELL_WIDTH_KEY, maxCellWidth > 0 ? maxCellWidth : null);
      }
    }

    myInvalidateWidestCellValues = false;

    if (!toLayOut.isEmpty()) {
      // This lays out columns which were already shown but have not been laid out after updateWidestCellValueCaches was called for them.
      // It happens when rows are being loaded in transposed mode.
      // (we first create a layout column, then we have newRowsAdded called so that the first layout is to be updated)
      // see TRP.addRows for details
      doLayout(toLayOut);
    }
  }

  private int getMaximumPreferredWidth(JBIterable<? extends ViewIndex<?>> viewRowIndices,
                                       ViewIndex<?> viewColumnIdx,
                                       int columnNum) {
    int widestCell = 0;
    int maxRowForSizeCalc = columnNum > 100 ? MAX_ROW_IDX_FOR_SIZE_CALCULATION_LOTS_OF_COLUMNS :
                            myResultView.isTransposed() ? MAX_ROW_IDX_FOR_SIZE_CALCULATION_TRANSPOSED :
                            MAX_ROW_IDX_FOR_SIZE_CALCULATION;
    int firstRowsForSizeCalc = columnNum > 100 ? FIRST_ROWS_FOR_SIZE_CALCULATION_LOTS_OF_COLUMNS :
                               myResultView.isTransposed() ? FIRST_ROWS_FOR_SIZE_CALCULATION_TRANSPOSED :
                               FIRST_ROWS_FOR_SIZE_CALCULATION;
    for (ViewIndex<?> viewRowIdx : viewRowIndices) {

      int rowIdxInt = viewRowIdx.toModel(myGrid).asInteger();
      if (rowIdxInt > maxRowForSizeCalc) break;
      if (rowIdxInt > firstRowsForSizeCalc && rowIdxInt % firstRowsForSizeCalc != 0) {
        continue;
      }

      ViewIndex<GridRow> rowIdx = myResultView.isTransposed() ? cast(viewColumnIdx) : cast(viewRowIdx);
      ViewIndex<GridColumn> columnIdx = myResultView.isTransposed() ? cast(viewRowIdx) : cast(viewColumnIdx);
      JComponent component = myResultView.getCellRendererComponent(rowIdx, columnIdx, false);
      if (component != null) {
        widestCell = Math.max(widestCell, component.getPreferredSize().width);
      }
    }
    return widestCell;
  }

  private static @NotNull <T> ViewIndex<T> cast(@NotNull ViewIndex<?> idx) {
    //noinspection unchecked
    return (ViewIndex<T>)idx;
  }

  private static @NotNull <T> ModelIndex<T> cast(@NotNull ModelIndex<?> idx) {
    //noinspection unchecked
    return (ModelIndex<T>)idx;
  }
}
