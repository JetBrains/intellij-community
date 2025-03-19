package com.intellij.database.datagrid;

import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ColumnNamesHierarchyNode;
import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.openapi.Disposable;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NestedTablesDataGridModel extends GridListModelBase<GridRow, GridColumn> implements GridModelWithNestedTables {
  private final GridListModelBase<GridRow, GridColumn> myModel;

  private final List<NestedTableCellCoordinate> myPathToSelectedNestedTable = new ArrayList<>();

  private NestedTable myCurrentlySelectedNestedTable;

  private final NestedTableCache<GridColumn> myColumnsCache = new NestedTableCache<>(new ColumnCacheStrategy());

  private final NestedTableCache<GridRow> myRowsCache = new NestedTableCache<>(new RowCacheStrategy());

  public NestedTablesDataGridModel(GridListModelBase<GridRow, GridColumn> model) {
    myModel = model;
    myCurrentlySelectedNestedTable = new GridWrapperNestedTable(myModel);
  }

  public void appendSelectedCellCoordinate(@NotNull NestedTableCellCoordinate coord) {
    Object cellValue = coord.myColumn.getValue(coord.myRow);
    if (!(cellValue instanceof NestedTable nestedTable)) {
      throw new IllegalArgumentException("Cell at coordinate [" + coord.getRowIdx() + ", " + coord.getColumnIdx() + "] is not an instance of NestedTable");
    }
    myPathToSelectedNestedTable.add(coord);
    myCurrentlySelectedNestedTable = nestedTable;
  }

  public @NotNull NestedTableCellCoordinate removeLastNCellCoordinates(int numCellsToRemove) {
    removeTail(myPathToSelectedNestedTable, numCellsToRemove - 1);
    int lastIndex = myPathToSelectedNestedTable.size() - 1;
    NestedTableCellCoordinate cellCoordinateToRestoreScrollPosition = myPathToSelectedNestedTable.remove(lastIndex);
    updateNestedTableSelection();
    return cellCoordinateToRestoreScrollPosition;
  }

  @Override
  public @NotNull List<NestedTableCellCoordinate> getPathToSelectedNestedTable() {
    return myPathToSelectedNestedTable;
  }

  public ColumnNamesHierarchyNode getColumnsHierarchyOfSelectedNestedTable() {
    return myCurrentlySelectedNestedTable.getColumnsHierarchy();
  }

  private void updateNestedTableSelection() {
    NestedTable currentNestedTable = new GridWrapperNestedTable(myModel);
    List<NestedTableCellCoordinate> validCellCoordinate = new ArrayList<>(myPathToSelectedNestedTable.size());

    for (NestedTableCellCoordinate cellCoordinate : myPathToSelectedNestedTable) {
      Object cellValue;
      try {
        cellValue = cellCoordinate.myColumn.getValue(cellCoordinate.myRow);
      }
      catch (RuntimeException e) {
        cellValue = null;
      }

      if (!(cellValue instanceof NestedTable)) {
        myPathToSelectedNestedTable.clear();
        myPathToSelectedNestedTable.addAll(validCellCoordinate);
        break;
      }
      validCellCoordinate.add(cellCoordinate);

      currentNestedTable = (NestedTable)cellValue;
    }

    myCurrentlySelectedNestedTable = currentNestedTable;
  }

  private static void removeTail(@NotNull List<?> list, int k) {
    list.subList(Math.max(0, list.size() - k), list.size()).clear();
  }

  @Override
  public boolean hasListeners() {
    return myModel.hasListeners();
  }

  @Override
  public @Nullable Object getValueAt(ModelIndex<GridRow> rowIdx, ModelIndex<GridColumn> columnIdx) {
    if (isTopLevelGrid()) return myModel.getValueAt(rowIdx, columnIdx);
    if (!isValidRowIdx(rowIdx) || !isValidColumnIdx(columnIdx)) return null;

    return myCurrentlySelectedNestedTable.getValueAt(rowIdx.asInteger(), columnIdx.asInteger());
  }

  @Override
  public boolean allValuesEqualTo(@NotNull ModelIndexSet<GridRow> rowIndices,
                                  @NotNull ModelIndexSet<GridColumn> columnIndices,
                                  @Nullable Object what) {
    if (!isTopLevelGrid()) {
      throw new IllegalStateException("Operation is not permitted for nested grids.");
    }
    return myModel.allValuesEqualTo(rowIndices, columnIndices, what);
  }

  @Override
  public @Nullable GridRow getRow(@NotNull ModelIndex<GridRow> row) {
    if (isTopLevelGrid()) return myModel.getRow(row);
    return getRow(row.asInteger());
  }

  @Override
  public @Nullable GridColumn getColumn(@NotNull ModelIndex<GridColumn> column) {
    if (isTopLevelGrid()) return myModel.getColumn(column);
    return new NestedTableColumn(column.asInteger(), myCurrentlySelectedNestedTable);
  }

  @Override
  public @NotNull JBIterable<GridColumn> getColumnsAsIterable() {
    return JBIterable.from(getGridColumns());
  }

  @Override
  public @NotNull List<GridColumn> getColumns() {
    return getGridColumns();
  }

  private @NotNull List<GridColumn> getGridColumns() {
    if (isTopLevelGrid()) {
      return myModel.getColumns();
    }

    return myColumnsCache.getOrCreateNestedTableItems(myCurrentlySelectedNestedTable);
  }

  @Override
  public @NotNull List<GridRow> getRows() {
    if (isTopLevelGrid()) {
      return myModel.getRows();
    }

    return myRowsCache.getOrCreateNestedTableItems(myCurrentlySelectedNestedTable);
  }

  @Override
  public int getColumnCount() {
    return isTopLevelGrid()
           ? myModel.getColumnCount()
           : getNumberOfColumns();
  }

  @Override
  public int getRowCount() {
    return isTopLevelGrid()
           ? myModel.getRowCount()
           : myCurrentlySelectedNestedTable.getRowsNum();
  }

  @Override
  public boolean isUpdatingNow() {
    return myModel.isUpdatingNow();
  }

  @Override
  public boolean isValidRowIdx(@NotNull ModelIndex<GridRow> rowIdx) {
    return isTopLevelGrid()
           ? myModel.isValidRowIdx(rowIdx)
           : isValidRowIdx(rowIdx.asInteger());
  }

  private boolean isValidRowIdx(int rowIdx) {
    return myCurrentlySelectedNestedTable.isValidRowIdx(rowIdx);
  }

  @Override
  public boolean isValidColumnIdx(@NotNull ModelIndex<GridColumn> columnIdx) {
    return isTopLevelGrid()
           ? myModel.isValidColumnIdx(columnIdx)
           : isValidColumnIdx(columnIdx.asInteger());
  }

  private boolean isValidColumnIdx(int columnIdx) {
    return columnIdx > -1 && columnIdx < getNumberOfColumns();
  }

  private int getNumberOfColumns() {
    return myCurrentlySelectedNestedTable.getColumnsNum();
  }

  @Override
  public void addListener(@NotNull Listener<GridRow, GridColumn> l, @NotNull Disposable disposable) {
    myModel.addListener(l, disposable);
  }

  @Override
  protected @Nullable Object getValueAt(@NotNull GridRow row, @NotNull GridColumn column) {
    if (isTopLevelGrid()) return myModel.getValueAt(row, column);
    return myCurrentlySelectedNestedTable.getValueAt(row, column);
  }

  @Override
  public boolean allValuesEqualTo(@NotNull List<CellMutation> mutations) {
    return myModel.allValuesEqualTo(mutations);
  }

  @Override
  public void setUpdatingNow(boolean updatingNow) {
    myModel.setUpdatingNow(updatingNow);
  }

  @Override
  public void addRows(@NotNull List<? extends GridRow> rows) {
    rows.forEach(row -> myCurrentlySelectedNestedTable.addRow(row));
    myRowsCache.clearCache(myCurrentlySelectedNestedTable);
  }

  @Override
  public void addRow(@NotNull GridRow row) {
    myCurrentlySelectedNestedTable.addRow(row);
    myRowsCache.clearCache(myCurrentlySelectedNestedTable);
  }

  @Override
  public void removeRows(int firstRowIndex, int rowCount) {
    if (isTopLevelGrid()) {
      myModel.removeRows(firstRowIndex, rowCount);
      return;
    }
    for (int i = firstRowIndex + rowCount - 1; i >= firstRowIndex; --i) {
      myCurrentlySelectedNestedTable.removeRow(i);
    }
    myRowsCache.clearCache(myCurrentlySelectedNestedTable);
    updateNestedTableSelection();
  }

  @Override
  public void setColumns(@NotNull List<? extends GridColumn> columns) {
    if (!isTopLevelGrid()) {
      throw new IllegalStateException(
        "The setColumns operation can only be performed on top-level tables. Nested tables do not support this operation because the column structure of nested tables is encapsulated within the nested table object.");
    }
    myModel.setColumns(columns);
    clearCaches();
  }

  @Override
  public void clearColumns() {
    if (!isTopLevelGrid()) {
      throw new IllegalStateException(
        "The setColumns operation can only be performed on top-level tables. Nested tables do not support this operation because the column structure of nested tables is encapsulated within the nested table object.");
    }
    myModel.clearColumns();
    clearCaches();
  }

  @Override
  public void set(int i, GridRow objects) {
    if (isTopLevelGrid()) {
      myModel.set(i, objects);
      return;
    }
    myCurrentlySelectedNestedTable.setRow(i, objects);
    updateNestedTableSelection();
    myRowsCache.clearCache(myCurrentlySelectedNestedTable);
  }

  private void clearCaches() {
    myColumnsCache.clearCache();
    myRowsCache.clearCache();
  }

  @Override
  public @NotNull List<GridRow> getRows(@NotNull ModelIndexSet<GridRow> indexSet) {
    if (isTopLevelGrid()) return myModel.getRows(indexSet);

    List<GridRow> result = new ArrayList<>();
    for (ModelIndex<GridRow> index : indexSet.asIterable()) {
      result.add(getRow(index.asInteger()));
    }

    return result;
  }

  private @Nullable GridRow getRow(int rowIdx) {
    return isValidRowIdx(rowIdx) ? new NestedTableRow(rowIdx, myCurrentlySelectedNestedTable) : null;
  }

  @Override
  public boolean isNestedTablesSupportEnabled() {
    return true;
  }

  @Override
  public boolean isTopLevelGrid() {
    return myPathToSelectedNestedTable.isEmpty();
  }

  @Override
  public void navigateIntoNestedTable(@NotNull NestedTableCellCoordinate cellCoordinate) {
    appendSelectedCellCoordinate(cellCoordinate);
  }

  // needs to be revisited after KTNB-376
  @Override
  public boolean isColumnContainsNestedTable(@NotNull GridColumn column) {
    Object columnValue = GridModelUtil.tryToFindNonNullValueInColumn(this, column, 20);
    return columnValue instanceof NestedTable;
  }

  @Override
  public NestedTable getSelectedNestedTable() {
    return myCurrentlySelectedNestedTable;
  }

  @Override
  public @NotNull NestedTableCellCoordinate navigateBackFromNestedTable(int numSteps) {
    return removeLastNCellCoordinates(numSteps);
  }

  public static class NestedTableCellCoordinate {
    private final GridRow myRow;

    private final GridColumn myColumn;

    public NestedTableCellCoordinate(@NotNull GridRow row, @NotNull GridColumn column) {
      myRow = row;
      myColumn = column;
    }

    public int getRowIdx() {
      return GridRow.toRealIdx(myRow);
    }

    public int getRowNum() {
      return myRow.getRowNum();
    }

    public GridRow getRow() {
      return myRow;
    }

    public int getColumnIdx() {
      return myColumn.getColumnNumber();
    }

    public ModelIndex<GridColumn> getColumnIdx(CoreGrid<?, GridColumn> grid) {
      return ModelIndex.forColumn(grid, myColumn.getColumnNumber());
    }

    public GridColumn getColumn() {
      return myColumn;
    }

    public String getColumnName() {
      return myColumn.getName();
    }
  }

  public static class NestedTableRow implements GridRow {
    private final int myRowIdx;

    private final NestedTable myNestedTable;

    NestedTableRow(int rowIdx, NestedTable table) {
      myNestedTable = table;
      if (!myNestedTable.isValidRowIdx(rowIdx)) {
        throw new IllegalStateException(
          String.format("Row index: %d. Error during nested table row creation: row index is invalid.", rowIdx));
      }
      myRowIdx = rowIdx;
    }

    @Override
    public @Nullable Object getValue(int columnNum) {
      if (!myNestedTable.isValidColumnIdx(columnNum)) {
        throw new IllegalStateException(
          String.format("Column number: %d. Error during retrieving value from nested table row: column number is out of bounds.",
                        columnNum));
      }
      return myNestedTable.getValueAt(myRowIdx, columnNum);
    }

    @Override
    public int getSize() {
      return myNestedTable.getColumnsNum();
    }

    @Override
    public void setValue(int i, @Nullable Object object) {
      if (!myNestedTable.isValidColumnIdx(i)) {
        throw new IllegalStateException(
          String.format("Column number: %d. Error during setting value in nested table row: column number is out of bounds.", i));
      }
      myNestedTable.setValueAt(myRowIdx, i, object);
    }

    @Override
    public int getRowNum() {
      return myNestedTable.getRowNum(myRowIdx);
    }
  }

  public static class NestedTableColumn implements GridColumn {
    private final int myColumnIdx;

    private final NestedTable myNestedTable;

    private int myType = Integer.MAX_VALUE;

    private String myTypeName = null;

    NestedTableColumn(int columnIdx, NestedTable table) {
      myNestedTable = table;
      if (!myNestedTable.isValidColumnIdx(columnIdx)) {
        throw new IllegalStateException(
          String.format("Column index: %d. Error during nested table column creation: column index is out of bounds.", columnIdx));
      }
      myColumnIdx = columnIdx;
    }

    @Override
    public int getColumnNumber() {
      return myColumnIdx;
    }

    @Override
    public int getType() {
      if (myType == Integer.MAX_VALUE) {
        myType = myNestedTable.getColumnType(myColumnIdx);
      }
      return myType;
    }

    @Override
    public String getName() {
      return myNestedTable.getColumnName(myColumnIdx);
    }

    @Override
    public @Nullable String getTypeName() {
      if (myTypeName == null) {
        myTypeName = myNestedTable.getColumnTypeName(myColumnIdx);
      }

      return myTypeName;
    }
  }

  private interface CacheStrategy<T> {
    T createNestedItem(int index);

    int getItemCount();
  }

  private static class NestedTableCache<T> {
    private static final int MAX_CACHE_SIZE = 3;

    private final CacheStrategy<T> cacheStrategy;

    private final Map<NestedTable, List<T>> myNestedTableItems =
      new LinkedHashMap<>(MAX_CACHE_SIZE + 1, .75F, true) {
        @Override
        public boolean removeEldestEntry(Map.Entry eldest) {
          return size() > MAX_CACHE_SIZE;
        }
      };

    NestedTableCache(CacheStrategy<T> cacheStrategy) {
      this.cacheStrategy = cacheStrategy;
    }

    public List<T> getOrCreateNestedTableItems(NestedTable currentNestedTable) {
      myNestedTableItems.computeIfAbsent(currentNestedTable, this::createNestedItems);
      return myNestedTableItems.get(currentNestedTable);
    }

    public void clearCache() {
      myNestedTableItems.clear();
    }

    public void clearCache(NestedTable currentNestedTable) {
      myNestedTableItems.remove(currentNestedTable);
    }

    private @NotNull List<T> createNestedItems(NestedTable currentNestedTable) {
      int itemCount = cacheStrategy.getItemCount();
      List<T> result = new ArrayList<>(itemCount);
      for (int i = 0; i < itemCount; i++) {
        result.add(cacheStrategy.createNestedItem(i));
      }
      return result;
    }
  }

  private class ColumnCacheStrategy implements CacheStrategy<GridColumn> {
    @Contract("_ -> new")
    @Override
    public @NotNull GridColumn createNestedItem(int index) {
      return new NestedTableColumn(index, myCurrentlySelectedNestedTable);
    }

    @Override
    public int getItemCount() {
      return getNumberOfColumns();
    }
  }

  private class RowCacheStrategy implements CacheStrategy<GridRow> {
    @Contract("_ -> new")
    @Override
    public @NotNull GridRow createNestedItem(int index) {
      return new NestedTableRow(index, myCurrentlySelectedNestedTable);
    }

    @Override
    public int getItemCount() {
      return getRowCount();
    }
  }
}
