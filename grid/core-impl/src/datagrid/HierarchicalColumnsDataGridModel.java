package com.intellij.database.datagrid;

import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate;
import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.dbimport.CsvImportUtil;
import com.intellij.database.dbimport.TypeMerger;
import com.intellij.database.extractors.GridExtractorsUtilCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.*;
import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.reverse;

public class HierarchicalColumnsDataGridModel extends GridListModelBase<GridRow, GridColumn> implements GridModelWithNestedTables {
  private final GridListModelBase<GridRow, GridColumn> myModel;

  private HierarchicalReader myHierarchicalReader;

  private List<HierarchicalGridColumn> myRoots = new ArrayList<>();

  private ColumnNamesHierarchyNode myTopLevelHierarchy;

  public HierarchicalColumnsDataGridModel(GridListModelBase<GridRow, GridColumn> model) {
    myModel = model;
  }

  @Override
  public @NotNull HierarchicalReader getHierarchicalReader() {
    if (myHierarchicalReader == null) {
      myHierarchicalReader = new HierarchicalReader(myRoots);
    }

    return myHierarchicalReader;
  }

  @Override
  public boolean isValidRowIdx(@NotNull ModelIndex<GridRow> rowIdx) {
    return myModel.isValidRowIdx(rowIdx);
  }

  @Override
  public boolean isValidColumnIdx(@NotNull ModelIndex<GridColumn> column) {
    int[] path = getHierarchicalReader().getColumnPath(column.asInteger());
    return getHierarchicalReader().isValidPath(path);
  }

  @Override
  public boolean allValuesEqualTo(@NotNull ModelIndexSet<GridRow> rowIndices,
                                  @NotNull ModelIndexSet<GridColumn> columnIndices,
                                  @Nullable Object what) {
    return myModel.allValuesEqualTo(rowIndices, columnIndices, what);
  }

  @Override
  public GridRow getRow(@NotNull ModelIndex<GridRow> row) {
    return myModel.getRow(row);
  }

  @Override
  protected @Nullable Object getValueAt(@NotNull GridRow row, @NotNull GridColumn column) {
    if (!(column instanceof HierarchicalGridColumn hierarchicalGridColumn)) {
      throw new IllegalArgumentException("The hierarchical column type is expected");
    }
    return getValue(row, hierarchicalGridColumn.getPathFromRoot());
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
  public @Nullable GridColumn getColumn(@NotNull ModelIndex<GridColumn> column) {
    int[] hierarchicalIdx = getHierarchicalReader().getColumnPath(column.asInteger());
    return getColumn(hierarchicalIdx);
  }

  private GridColumn getColumn(int[] columnIdx) {
    List<HierarchicalGridColumn> columns = myRoots;
    HierarchicalGridColumn cur = null;
    for (int idx : columnIdx) {
      if (idx < 0 || idx >= columns.size()) return null;
      cur = columns.get(idx);
      columns = cur.getChildren();
    }

    return cur;
  }

  @Override
  public @NotNull List<GridRow> getRows(@NotNull ModelIndexSet<GridRow> rows) {
    return myModel.getRows(rows);
  }

  @Override
  public @NotNull List<GridColumn> getColumns() {
    return ContainerUtil.map(getHierarchicalReader()
      .getLeafs(), column -> column);
  }

  public @Nullable List<HierarchicalGridColumn> getTopLevelColumns() {
    return myRoots;
  }

  @Override
  public boolean isNestedTablesSupportEnabled() {
    return myModel instanceof NestedTablesDataGridModel;
  }

  @Override
  public boolean isTopLevelGrid() {
    if (!(myModel instanceof NestedTablesDataGridModel nestedTablesDataGridModel)) return true;
    return nestedTablesDataGridModel.isTopLevelGrid();
  }

  @Override
  public void navigateIntoNestedTable(@NotNull NestedTableCellCoordinate cellCoordinate) {
    if (!(myModel instanceof NestedTablesDataGridModel nestedTablesDataGridModel)) return;
    nestedTablesDataGridModel.appendSelectedCellCoordinate(cellCoordinate);
    replaceColumnNameHierarchy(nestedTablesDataGridModel.getColumnsHierarchyOfSelectedNestedTable());
  }

  @Override
  public @Nullable NestedTableCellCoordinate navigateBackFromNestedTable(int numSteps) {
    if (!(myModel instanceof NestedTablesDataGridModel nestedTablesDataGridModel)) return null;
    NestedTableCellCoordinate cellCoordinateToRestoreScroll = nestedTablesDataGridModel.removeLastNCellCoordinates(numSteps);
    updateHierarchyFromNestedTable(nestedTablesDataGridModel);

    return cellCoordinateToRestoreScroll;
  }

  private void updateHierarchyFromNestedTable(NestedTablesDataGridModel nestedTablesDataGridModel) {
    if (nestedTablesDataGridModel.isTopLevelGrid()) {
      restoreOriginalColumnNamesHierarchy();
    } else {
      replaceColumnNameHierarchy(nestedTablesDataGridModel.getColumnsHierarchyOfSelectedNestedTable());
    }
  }

  @Override
  public @NotNull List<NestedTableCellCoordinate> getPathToSelectedNestedTable() {
    if (!(myModel instanceof NestedTablesDataGridModel nestedTablesDataGridModel)) return emptyList();
    return nestedTablesDataGridModel.getPathToSelectedNestedTable();
  }

  // needs to be revisited after KTNB-376
  @Override
  public boolean isColumnContainsNestedTable(@NotNull GridColumn column) {
    Object columnValue = GridModelUtil.tryToFindNonNullValueInColumn(this, column, 20);
    return columnValue instanceof NestedTable;
  }

  @Override
  public NestedTable getSelectedNestedTable() {
    if (!(myModel instanceof NestedTablesDataGridModel nestedTablesDataGridModel)) return null;
    return nestedTablesDataGridModel.getSelectedNestedTable();
  }

  @Override
  public void addRows(@NotNull List<? extends GridRow> rows) {
    myModel.addRows(rows);
  }

  @Override
  public void addRow(@NotNull GridRow objects) {
    myModel.addRow(objects);
  }

  @Override
  public void removeRows(int firstRowIndex, int rowCount) {
    if (!(myModel instanceof NestedTablesDataGridModel nestedTablesDataGridModel)) {
      myModel.removeRows(firstRowIndex, rowCount);
      return;
    }

    List<NestedTableCellCoordinate> prevPath =
      new ArrayList<>(nestedTablesDataGridModel.getPathToSelectedNestedTable());
    myModel.removeRows(firstRowIndex, rowCount);
    if (!nestedTablesDataGridModel.getPathToSelectedNestedTable().equals(prevPath)) {
      updateHierarchyFromNestedTable(nestedTablesDataGridModel);
    }
  }

  @Override
  public @NotNull JBIterable<GridColumn> getColumnsAsIterable() {
    return JBIterable.from(Collections.unmodifiableList(getColumns()));
  }

  @Override
  public @NotNull JBIterable<GridColumn> getColumnsAsIterable(@NotNull ModelIndexSet<GridColumn> columns) {
    return columns.asIterable().transform(this::getColumn);
  }

  @Override
  public @NotNull List<GridRow> getRows() {
    return myModel.getRows();
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getRowIndices() {
    return myModel.getRowIndices();
  }

  @Override
  public void setColumns(@NotNull List<? extends GridColumn> columns) {
    myModel.setColumns(columns);
    myHierarchicalReader = null;
  }

  @Override
  public void clearColumns() {
    myModel.clearColumns();
    myHierarchicalReader = null;
  }

  @Override
  public void set(int i, GridRow objects) {
    if (!(myModel instanceof NestedTablesDataGridModel nestedTablesDataGridModel)) {
      myModel.set(i, objects);
      return;
    }

    List<NestedTableCellCoordinate> prevPath =
      new ArrayList<>(nestedTablesDataGridModel.getPathToSelectedNestedTable());
    myModel.set(i, objects);
    if (!nestedTablesDataGridModel.getPathToSelectedNestedTable().equals(prevPath)) {
      updateHierarchyFromNestedTable(nestedTablesDataGridModel);
    }
  }

  @Override
  public int getColumnCount() {
    return getHierarchicalReader().getLeafColumnsCount();
  }

  @Override
  public int getRowCount() {
    return myModel.getRowCount();
  }

  @Override
  public boolean isUpdatingNow() {
    return myModel.isUpdatingNow();
  }

  @Override
  public void addListener(@NotNull Listener<GridRow, GridColumn> l, @NotNull Disposable disposable) {
    myModel.addListener(l, disposable);
  }

  @Override
  public boolean hasListeners() {
    return myModel.hasListeners();
  }

  @Override
  public @NotNull List<GridColumn> getAllColumnsForExtraction(int... selection) {
    Set<Integer> selectedIndices = GridExtractorsUtilCore.intArrayToSet(selection);
    return selectedIndices.size() == 1
           ? getColumns()
           : getRootColumnsForExtraction(selectedIndices);
  }

  /**
   * In the case of extracting data from multiple columns,
   * this method returns the root elements of the hierarchy
   * to preserve the structure of the table during extraction.
   * See {@link ExtractorHierarchicalGridColumnImpl#getValue(GridRow)} for more information.
   */
  private @NotNull List<GridColumn> getRootColumnsForExtraction(@NotNull Set<Integer> selectedIndices) {
    List<GridColumn> result = new ArrayList<>();
    for (int i = 0; i < myRoots.size(); i++) {
      result.add(new ExtractorHierarchicalGridColumnImpl(myRoots.get(i), i, selectedIndices));
    }
    return result;
  }

  public void setOriginalColumnNamesHierarchy(@NotNull ColumnNamesHierarchyNode root) {
    myTopLevelHierarchy = root;
    constructHierarchicalColumnsFromHierarchy(myTopLevelHierarchy);
  }

  private void constructHierarchicalColumnsFromHierarchy(ColumnNamesHierarchyNode root) {
    List<ColumnNamesHierarchyNode> children = root.getChildren();
    int[] globalIdx = new int[] {0};
    myRoots = IntStream.range(0, children.size())
      .mapToObj(idx -> constructHierarchicalColumn(children.get(idx), globalIdx, new int[]{idx}, null))
      .toList();
    myHierarchicalReader = null;
  }

  public void restoreOriginalColumnNamesHierarchy() {
    if (myTopLevelHierarchy == null) { throw new IllegalStateException(); }
    constructHierarchicalColumnsFromHierarchy(myTopLevelHierarchy);
  }

  private void replaceColumnNameHierarchy(@NotNull ColumnNamesHierarchyNode root) {
    constructHierarchicalColumnsFromHierarchy(root);
    updateColumnTypes();
  }

  private HierarchicalGridColumn constructHierarchicalColumn(ColumnNamesHierarchyNode node,
                                                             int[] globalIdx,
                                                             int[] path,
                                                             HierarchicalGridColumn parent) {
    HierarchicalGridColumn column;
    if (node.getChildren().isEmpty()) {
      column = new HierarchicalGridColumn(node.getName(), globalIdx[0]++, path, parent);
    }
    else {
      column = new HierarchicalGridColumn(node.getName(), path, parent);
    }

    List<HierarchicalGridColumn> children = new ArrayList<>();
    for (int i = 0; i < node.getChildren().size(); i++) {
      ColumnNamesHierarchyNode n = node.getChildren().get(i);
      HierarchicalGridColumn child = constructHierarchicalColumn(n, globalIdx, addIntToArray(path, i), column);
      children.add(child);
    }
    column.setChildren(children);

    return column;
  }

  public boolean updateColumnTypes() {
    boolean typesUpdated = false;
    for (HierarchicalGridColumn root : myRoots) {
      typesUpdated |= updateColumnTypesRecursively(root);
    }

    return typesUpdated;
  }

  private boolean updateColumnTypesRecursively(HierarchicalGridColumn column) {
    boolean typeChanged = false;

    TypeMerger merger = determineColumnType(getRows(), column.getPathFromRoot());
    typeChanged |= column.getType() != getType(merger);
    column.setType(getType(merger));
    column.setTypeName(merger.getName());

    for (HierarchicalGridColumn c : column.getChildren()) {
      typeChanged |= updateColumnTypesRecursively(c);
    }

    return typeChanged;
  }

  private static TypeMerger determineColumnType(List<GridRow> rows, int[] columnPath) {
    return determineColumnType(ContainerUtil.map(rows, GridRow::getValues).toArray(Object[][]::new), columnPath);
  }

  public static TypeMerger determineColumnType(Object[][] rowsValues, int[] columnPath) {
    List<String> values = IntStream.range(0, rowsValues.length)
      .mapToObj(rowIdx -> {
        Object[] rowValues = rowsValues[rowIdx];
        validateHierarchicalIndex(columnPath, rowIdx+1);
        Object value = tryExtractValueByHierarchicalIndex(rowValues, columnPath, rowIdx+1);

        return value == null ? null : value.toString();
      })
      .limit(200)
      .collect(Collectors.toList());

    return CsvImportUtil.getPreferredTypeMergerBasedOnContent(
      values, STRING_MERGER, INTEGER_MERGER, BIG_INTEGER_MERGER, DOUBLE_MERGER, BOOLEAN_MERGER);
  }

  private static Object getValue(GridRow row, int[] hierarchicalIdx) {
    validateHierarchicalIndex(hierarchicalIdx, row.getRowNum());
    return tryExtractValueByHierarchicalIndex(GridRow.getValues(row), hierarchicalIdx, row.getRowNum());
  }

  private static void validateHierarchicalIndex(int[] hierarchicalIdx, int rowNum) {
    if (hierarchicalIdx == null) {
      throw new IllegalArgumentException(
        String.format("Row %d failed: hierarchicalIdx cannot be null", rowNum)
      );
    }

    if (hierarchicalIdx.length == 0) {
      throw new IllegalArgumentException(
        String.format("Row %d access failed: hierarchicalIdx cannot be empty", rowNum)
      );
    }
  }

  private static @Nullable Object tryExtractValueByHierarchicalIndex(Object[] topLevelValues, int[] hierarchicalIdx, int rowNum) {
    try {
      return extractValueByHierarchicalIndex(topLevelValues, hierarchicalIdx);
    } catch (IndexOutOfBoundsException | NullPointerException | IllegalArgumentException e) {
      throw new RuntimeException(String.format("Row %d access failed: %s", rowNum, e.getMessage()), e);
    }
  }

  public static Object extractValueByHierarchicalIndex(Object[] topLevelValues, int[] hierarchicalIdx) {
    Object result = null;
    Object[] data = topLevelValues;
    for (int i = 0; i < hierarchicalIdx.length; ++i) {
      int idx = hierarchicalIdx[i];
      if (idx < 0 || idx >= data.length) {
        throw new IndexOutOfBoundsException(
          String.format(
            "Access by hierarchical idx %s failed: Index out of bounds at element by path %s",
            Arrays.toString(hierarchicalIdx),
            Arrays.toString(Arrays.copyOfRange(hierarchicalIdx, 0, i))
          )
        );
      }

      result = data[idx];

      if (result == null && i == hierarchicalIdx.length - 1) {
        return null;
      }
      else if (result == null) {
        throw new NullPointerException(
          String.format(
            "Access by hierarchical idx %s failed: Encountered a null element by path %s.",
            Arrays.toString(hierarchicalIdx),
            Arrays.toString(Arrays.copyOfRange(hierarchicalIdx, 0, i))
          )
        );
      }

      if (result instanceof Object[] vals) {
        data = vals;
        continue;
      }

      if (result instanceof List<?> vals) {
        data = vals.toArray();
        continue;
      }

      if (result instanceof Collection) {
        throw new NullPointerException(
          String.format(
            "Access by hierarchical idx %s failed: Encountered an unexpected nested collection by path %s. Only arrays or lists are allowed as nested collections",
            Arrays.toString(hierarchicalIdx),
            Arrays.toString(Arrays.copyOfRange(hierarchicalIdx, 0, i))
          )
        );
      }

      break;
    }

    return result;
  }

  private static int[] addIntToArray(int[] array, int value) {
    int[] newArray = new int[array.length + 1];
    System.arraycopy(array, 0, newArray, 0, array.length);
    newArray[array.length] = value;
    return newArray;
  }

  public class HierarchicalGridColumn implements GridColumn {
    private final String myName;
    private List<HierarchicalGridColumn> myChildren;
    private final int[] myPathFromRoot;

    private final int myColumnIdx;

    private int myType;

    private String myTypeName;

    private final HierarchicalGridColumn myParent;

    HierarchicalGridColumn(@NotNull String name, int @NotNull [] pathFromRoot, @Nullable HierarchicalGridColumn parent) {
      myName = name;
      myPathFromRoot = pathFromRoot;
      myColumnIdx = -1;
      myType = DocumentDataHookUp.DataMarkup.getType(STRING_MERGER);
      myTypeName = STRING_MERGER.getName();
      myParent = parent;
    }

    HierarchicalGridColumn(@NotNull String name, int leafIdx, int @NotNull [] pathFromRoot, @Nullable HierarchicalGridColumn parent) {
      myName = name;
      myPathFromRoot = pathFromRoot;
      myColumnIdx = leafIdx;
      myType = DocumentDataHookUp.DataMarkup.getType(STRING_MERGER);
      myTypeName = STRING_MERGER.getName();
      myParent = parent;
    }

    @Override
    public Object getValue(@NotNull GridRow row) {
      return getValueAt(row, this);
    }

    public @NotNull List<HierarchicalGridColumn> getLeaves() {
      return getHierarchicalReader().getAllLeafNodesInSubtree(this);
    }

    public List<HierarchicalGridColumn> getSiblings() {
      return getHierarchicalReader().getSiblings(this);
    }

    public void setChildren(@NotNull List<HierarchicalGridColumn> children) {
      myChildren = children;
    }

    public int @NotNull [] getPathFromRoot() {
      return myPathFromRoot;
    }

    public @NotNull List<HierarchicalGridColumn> getChildren() {
      return myChildren == null ? emptyList() : myChildren;
    }

    @Override
    public int getColumnNumber() {
      return myColumnIdx;
    }

    @Override
    public int getType() {
      return myType;
    }

    @Override
    public @NotNull @NlsSafe String getName() {
      return myName;
    }

    public @NlsSafe @Nullable String getNameOfAncestor(int depth) {
      int currentDepth = myPathFromRoot.length - 1;
      HierarchicalGridColumn ancestor = depth == currentDepth - 1
                                        ? getParent()
                                        : getHierarchicalReader().getAncestorAtDepth(this, depth);

      return ancestor == null ? null : ancestor.getName();
    }

    public @Nullable HierarchicalGridColumn getAncestorAtDepth(int depth) {
      int currentDepth = myPathFromRoot.length - 1;
      return depth == currentDepth - 1 ? getParent() : getHierarchicalReader().getAncestorAtDepth(this, depth);
    }

    @Override
    public @NlsSafe @Nullable String getTypeName() {
      return myTypeName;
    }

    public void setType(int type) {
      if (type != myType) myType = type;
    }

    public void setTypeName(String name) {
      if (!name.equals(myTypeName)) myTypeName = name;
    }

    public boolean isTopLevelColumn() {
      return myPathFromRoot.length == 1;
    }

    public boolean isLeftMostChildOfDirectAncestor() {
      return isChildAtPositionOfDirectAncestor(0);
    }

    public boolean isRightMostChildOfAncestor(HierarchicalGridColumn ancestor, @NotNull Predicate<HierarchicalGridColumn> shouldSkip) {
      if (ancestor == null) return false;

      HierarchicalGridColumn rightMost = ancestor;
      while (rightMost != null && !rightMost.isLeaf()) {
        rightMost = getRightMostChild(rightMost, shouldSkip);
      }

      return rightMost == this;
    }

    private @Nullable HierarchicalGridColumn getRightMostChild(@NotNull HierarchicalGridColumn ancestor,
                                                               @NotNull Predicate<HierarchicalGridColumn> shouldSkip) {
      List<HierarchicalGridColumn> children = ancestor.getChildren();
      for (int i = children.size() - 1; i >= 0; i--) {
        HierarchicalGridColumn child = children.get(i);
        if (!shouldSkip.test(child)) {
          return child;
        }
      }

      return null;
    }

    private boolean isChildAtPositionOfDirectAncestor(int position) {
      HierarchicalGridColumn ancestor = getParent();
      if (ancestor == null) return true;

      List<HierarchicalGridColumn> children = ancestor.getChildren();
      if (children.isEmpty()) return false;

      int index = position < 0 ? children.size() + position : position;

      return children.get(index).equals(this);
    }

    public @Nullable HierarchicalGridColumn getParent() {
      return myParent;
    }

    public @NotNull List<String> getFullyQualifiedName() {
      List<String> columnsNames = new ArrayList<>();
      HierarchicalGridColumn current = this;
      while (current != null) {
        columnsNames.add(current.getName());
        current = current.getParent();
      }

      return reverse(columnsNames);
    }

    public boolean isLeaf() {
      return myChildren == null || myChildren.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      HierarchicalGridColumn column = (HierarchicalGridColumn)o;
      return myColumnIdx == column.myColumnIdx &&
             Objects.equals(myName, column.myName) &&
             Arrays.equals(myPathFromRoot, column.myPathFromRoot);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(myName, myColumnIdx);
      result = 31 * result + Arrays.hashCode(myPathFromRoot);
      return result;
    }
  }

  public interface ExtractorHierarchicalGridColumn extends GridColumn {
    boolean isMatchesSelection();
  }

  public class ExtractorHierarchicalGridColumnImpl implements ExtractorHierarchicalGridColumn {
    private final HierarchicalGridColumn myColumn;

    private final Set<Integer> mySelectedColumns;

    private final int myColNumber;

    ExtractorHierarchicalGridColumnImpl(@NotNull HierarchicalGridColumn column, int columnNumber, @NotNull Set<Integer> selectedColumns) {
      myColumn = column;
      myColNumber = columnNumber;
      mySelectedColumns = selectedColumns;
    }

    @Override
    public @Nullable Object getValue(@NotNull GridRow row) {
      HierarchicalGridColumn root = myColumn;

      if (root.getChildren().isEmpty()) {
        return root.getValue(row);
      }

      Map<String, Object> result = new HashMap<>();
      for (HierarchicalGridColumn c : root.getChildren()) {
        dfs(row, c, result);
      }

      return result;
    }

    private void dfs(GridRow row, HierarchicalGridColumn node, Map<String, Object> result) {
      if (node.getChildren().isEmpty()) {
        if (!mySelectedColumns.isEmpty() && !mySelectedColumns.contains(node.getColumnNumber())) return;
        Object value = node.getValue(row);
        result.put(node.getName(), value);
        return;
      }

      Map<String, Object> nested = new HashMap<>();
      for (HierarchicalGridColumn c : node.getChildren()) {
        dfs(row, c, nested);
      }

      result.put(node.getName(), nested);
    }

    @Override
    public boolean isMatchesSelection() {
      if (mySelectedColumns.isEmpty()) return true;

      if (myColumn.getChildren().isEmpty()) {
        return mySelectedColumns.contains(myColumn.getColumnNumber());
      }
      List<HierarchicalGridColumn> leaves = myColumn.getLeaves();
      for (HierarchicalGridColumn leaf : leaves) {
        if (mySelectedColumns.contains(leaf.getColumnNumber())) return true;
      }

      return false;
    }

    @Override
    public int getColumnNumber() {
      return myColNumber;
    }

    @Override
    public int getType() {
      return myColumn.getType();
    }

    @Override
    public String getName() {
      return myColumn.getName();
    }

    @Override
    public @Nullable String getTypeName() {
      return myColumn.getTypeName();
    }
  }

  public interface ColumnNamesHierarchyNode {
    @NotNull String getName();

    @NotNull List<ColumnNamesHierarchyNode> getChildren();
  }
}
