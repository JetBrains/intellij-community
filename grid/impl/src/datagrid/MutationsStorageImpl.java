package com.intellij.database.datagrid;

import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.datagrid.mutating.MutationData;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.database.run.ui.grid.editors.UnparsedValue;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public class MutationsStorageImpl implements MutationsStorage {
  private static final double MULTIPLIER = 1.5;

  private final double myMultiplier;
  private final GridModel<GridRow, GridColumn> myModel;
  private final RowsCounter myModifiedRowsCounter;
  private final RowsCounter myRowsWithUnparsedValuesCounter;
  private final Queue<ModelIndex<GridRow>> myInsertedRows;
  private final HashMap<ModelIndex<GridColumn>, GridColumn> myInsertedColumns;
  private final Set<ModelIndex<GridRow>> myDeletedRows;
  private final Set<ModelIndex<GridColumn>> myDeletedColumns;

  private MutationData[][] myValues;

  private int myMaxRows;
  private int myRows;
  private int myMaxColumns;
  private int myColumns;

  public MutationsStorageImpl(@NotNull GridModel<GridRow, GridColumn> model, int rows, int columns) {
    this(model, rows, columns, MULTIPLIER);
  }

  private MutationsStorageImpl(@NotNull GridModel<GridRow, GridColumn> model, int rows, int columns, double multiplier) {
    myMultiplier = multiplier;
    myRows = rows;
    myColumns = columns;
    myMaxRows = getIncreasedValue(rows);
    myMaxColumns = getIncreasedValue(columns);
    myModel = model;
    myValues = new MutationData[myMaxRows][];
    myModifiedRowsCounter = new ModifiedRowsCounter();
    myRowsWithUnparsedValuesCounter = new RowsWithUnparsedValuesCounter();
    myInsertedRows = new PriorityQueue<>((o1, o2) -> Integer.compare(o2.asInteger(), o1.asInteger()));
    myDeletedRows = new HashSet<>();
    myDeletedColumns = new HashSet<>();
    myInsertedColumns = new HashMap<>();
  }

  @Override
  public void set(@NotNull ModelIndex<GridRow> row,
                  @NotNull ModelIndex<GridColumn> column,
                  @Nullable CellMutation value) {
    if (!isValid(row, column)) return;
    allocateSpace(row, column);
    countModifications(value, row, column);
    myValues[row.asInteger()][column.asInteger()] = value == null ? null : new MutationData(value.getValue());
  }

  protected void allocateSpace(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    int rowIdx = row.asInteger();
    int colIdx = column.asInteger();
    if (rowIdx >= myMaxRows || colIdx >= myMaxColumns) reallocate(Math.max(rowIdx, myMaxRows), Math.max(colIdx, myMaxColumns));
    increase(rowIdx, colIdx);
    if (myValues[rowIdx] == null) myValues[rowIdx] = new MutationData[myMaxColumns];
  }

  private void shiftUpColumn(@NotNull ModelIndex<GridColumn> column) {
    myModifiedRowsCounter.deleteColumn(column);
    myRowsWithUnparsedValuesCounter.deleteColumn(column);
    int idx = column.asInteger();
    for (int i = 0; i < myValues.length; i++) {
      if (myValues[i] == null) continue;
      System.arraycopy(myValues[i], idx + 1, myValues[i], idx, myColumns - 1 - idx);
      myValues[i][myColumns - 1] = null;
    }
  }

  private void shiftUpRow(@NotNull ModelIndex<GridRow> row) {
    int idx = row.asInteger();
    System.arraycopy(myValues, idx + 1, myValues, idx, myRows - 1 - idx);
    myValues[myRows - 1] = null;
    myModifiedRowsCounter.shiftUp(row);
    myRowsWithUnparsedValuesCounter.shiftUp(row);
  }

  @Override
  public @NotNull Set<ModelIndex<GridRow>> getModifiedRows() {
    Set<ModelIndex<GridRow>> modifiedRows = new HashSet<>();
    for (int i : myModifiedRowsCounter.get()) {
      modifiedRows.add(ModelIndex.forRow(myModel, i));
    }
    return modifiedRows;
  }

  @Override
  public void deleteColumn(@NotNull ModelIndex<GridColumn> columnIdx) {
    if (!isValidColumn(columnIdx)) return;
    boolean isInserted = isInsertedColumn(columnIdx);
    clearColumn(columnIdx);
    if (isInserted) {
      insertedColumnRemoved(columnIdx);
    }
    else {
      myDeletedColumns.add(columnIdx);
    }
  }

  @Override
  public void deleteRow(@NotNull ModelIndex<GridRow> rowIdx) {
    if (!isValidRow(rowIdx)) return;
    boolean isInserted = isInsertedRow(rowIdx);
    clearRow(rowIdx);
    if (isInserted) {
      insertedRowRemoved(rowIdx);
    }
    else {
      myDeletedRows.add(rowIdx);
    }
  }

  private void insertedColumnRemoved(ModelIndex<GridColumn> columnIdx) {
    List<ModelIndex<GridColumn>> indexesToShift = findBiggerIndices(myInsertedColumns.keySet(), columnIdx);
    Map<ModelIndex<GridColumn>, GridColumn> columnsToShift =
      ContainerUtil.map2Map(indexesToShift, key -> new Pair<>(key, myInsertedColumns.get(key)));
    for (ModelIndex<GridColumn> idx : indexesToShift) {
      myInsertedColumns.remove(idx);
    }
    JBIterable.from(columnsToShift.entrySet())
      .transform(keyValue -> new Pair<>(ModelIndex.forColumn(myModel, keyValue.getKey().asInteger() - 1), keyValue.getValue()))
      .filter(keyValue -> keyValue.getFirst().asInteger() >= 0)
      .forEach(keyValue -> myInsertedColumns.put(keyValue.getFirst(), keyValue.getSecond()));
    shiftUpColumn(columnIdx);
  }

  private void insertedRowRemoved(ModelIndex<GridRow> rowIdx) {
    List<ModelIndex<GridRow>> indexesToShift = findBiggerIndices(myInsertedRows, rowIdx);
    myInsertedRows.removeAll(indexesToShift);
    JBIterable.from(indexesToShift)
      .transform(idx -> ModelIndex.forRow(myModel, idx.asInteger() - 1))
      .filter(idx -> idx.asInteger() >= 0)
      .forEach(myInsertedRows::add);
    shiftUpRow(rowIdx);
  }

  private static @Unmodifiable @NotNull <T> List<ModelIndex<T>> findBiggerIndices(@NotNull Collection<ModelIndex<T>> indexes, @NotNull ModelIndex<T> idx) {
    return ContainerUtil.filter(indexes, i -> i.asInteger() > idx.asInteger());
  }

  @Override
  public boolean isModified(@NotNull ModelIndex<GridRow> row) {
    return isValidRow(row) && myModifiedRowsCounter.contains(row.asInteger());
  }

  @Override
  public @Nullable MutationData get(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    return getInner(row, column);
  }

  private @Nullable MutationData getInner(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    int rowIdx = row.asInteger();
    int colIdx = column.asInteger();
    return !isValid(row, column) || rowIdx >= myRows || colIdx >= myColumns || myValues[rowIdx] == null ? null : myValues[rowIdx][colIdx];
  }

  @Override
  public boolean hasChanges() {
    return !myModifiedRowsCounter.isEmpty() || !myInsertedRows.isEmpty() || !myDeletedRows.isEmpty() || !myDeletedColumns.isEmpty();
  }

  @Override
  public int getModifiedRowsCount() {
    return myModifiedRowsCounter.myRowsSet.size();
  }

  private boolean isValidRow(@Nullable ModelIndex<GridRow> row) {
    return isValid(row, null);
  }

  private boolean isValidColumn(@Nullable ModelIndex<GridColumn> column) {
    return isValid(null, column);
  }

  @Override
  public boolean isValid(@Nullable ModelIndex<GridRow> row, @Nullable ModelIndex<GridColumn> column) {
    return (row == null || row.isValid(myModel) || isInsertedRow(row)) &&
           (column == null || column.isValid(myModel) || isInsertedColumn(column));
  }

  private void increase(int rows, int columns) {
    myRows = rows >= myRows ? rows + 1 : myRows;
    myColumns = columns >= myColumns ? columns + 1 : myColumns;
  }

  private void countModifications(@Nullable CellMutation value, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    MutationData oldData = myValues[row.asInteger()] == null ? null : myValues[row.asInteger()][column.asInteger()];
    myModifiedRowsCounter.countModifications(value, oldData, row, column);
    myRowsWithUnparsedValuesCounter.countModifications(value, oldData, row, column);
  }

  private void reallocate(int rows, int columns) {
    myMaxRows = rows >= myMaxRows ? getIncreasedValue(rows) : myMaxRows;
    myMaxColumns = columns >= myMaxColumns ? getIncreasedValue(columns) : myMaxColumns;
    increase(rows, columns);
    myValues = copy(myValues, myMaxRows, myMaxColumns);
    myModifiedRowsCounter.reallocate();
    myRowsWithUnparsedValuesCounter.reallocate();
  }

  @Override
  public boolean hasUnparsedValues() {
    return !myRowsWithUnparsedValuesCounter.isEmpty();
  }

  @Override
  public boolean hasUnparsedValues(ModelIndex<GridRow> row) {
    return myRowsWithUnparsedValuesCounter.contains(row.asInteger());
  }

  @Override
  public boolean isInsertedRow(@NotNull ModelIndex<GridRow> row) {
    return myInsertedRows.contains(row);
  }

  @Override
  public boolean isInsertedColumn(@NotNull ModelIndex<GridColumn> idx) {
    return myInsertedColumns.containsKey(idx);
  }

  @Override
  public int getInsertedRowsCount() {
    return myInsertedRows.size();
  }

  @Override
  public int getInsertedColumnsCount() {
    return myInsertedColumns.size();
  }

  @Override
  public int getDeletedRowsCount() {
    return myDeletedRows.size();
  }

  @Override
  public int getDeletedColumnsCount() {
    return myDeletedColumns.size();
  }

  @Override
  public boolean isDeletedRow(@NotNull ModelIndex<GridRow> row) {
    return myDeletedRows.contains(row);
  }

  @Override
  public boolean isDeletedColumn(@NotNull ModelIndex<GridColumn> column) {
    return myDeletedColumns.contains(column);
  }

  @Override
  public boolean isDeletedRows(ModelIndexSet<GridRow> rows) {
    return myDeletedRows.containsAll(rows.asList());
  }

  @Override
  public @Nullable ModelIndex<GridRow> getLastInsertedRow() {
    return myInsertedRows.peek();
  }

  @Override
  public JBIterable<ModelIndex<GridRow>> getDeletedRows() {
    return JBIterable.from(myDeletedRows);
  }

  @Override
  public JBIterable<ModelIndex<GridColumn>> getDeletedColumns() {
    return JBIterable.from(myDeletedColumns);
  }

  @Override
  public JBIterable<ModelIndex<GridRow>> getInsertedRows() {
    return JBIterable.from(myInsertedRows);
  }

  @Override
  public JBIterable<ModelIndex<GridColumn>> getInsertedColumns() {
    return JBIterable.from(myInsertedColumns.keySet());
  }

  @Override
  public void insertRow(@NotNull ModelIndex<GridRow> row) {
    myInsertedRows.add(row);
  }

  @Override
  public void insertColumn(@NotNull ModelIndex<GridColumn> idx, @NotNull GridColumn column) {
    myInsertedColumns.put(idx, column);
  }

  @Override
  public void renameColumn(@NotNull ModelIndex<GridColumn> idx, @NotNull String newName) {
    GridColumn column = myInsertedColumns.get(idx);
    if (column == null) return;
    String className = column instanceof JdbcColumnDescriptor ? ((JdbcColumnDescriptor)column).getJavaClassName() : null;
    myInsertedColumns.put(idx, new DataConsumer.Column(idx.asInteger(), newName, column.getType(), column.getTypeName(), className));
  }

  @Override
  public void removeColumnFromDeleted(@NotNull ModelIndex<GridColumn> index) {
    myDeletedColumns.remove(index);
  }

  private void clearColumn(@NotNull ModelIndex<GridColumn> column) {
    myInsertedColumns.remove(column);
    myDeletedColumns.remove(column);
    myModifiedRowsCounter.deleteColumn(column);
    myRowsWithUnparsedValuesCounter.deleteColumn(column);
    for (int i = 0; i < myMaxRows; i++) {
      if (myValues[i] == null) continue;
      myValues[i][column.asInteger()] = null;
    }
  }

  @Override
  public void removeRowFromDeleted(@NotNull ModelIndex<GridRow> index) {
    myDeletedRows.remove(index);
  }

  @Override
  public void clearRow(@NotNull ModelIndex<GridRow> rowIdx) {
    myInsertedRows.remove(rowIdx);
    myDeletedRows.remove(rowIdx);
    if (rowIdx.asInteger() < myRows) {
      myValues[rowIdx.asInteger()] = null;
    }
    myModifiedRowsCounter.deleteRow(rowIdx);
    myRowsWithUnparsedValuesCounter.deleteRow(rowIdx);
  }

  @Override
  public void clearColumns() {
    myInsertedColumns.clear();
    myDeletedColumns.clear();
  }

  @Override
  public @Nullable GridColumn getInsertedColumn(ModelIndex<GridColumn> idx) {
    return myInsertedColumns.get(idx);
  }

  private static MutationData @NotNull [][] copy(MutationData @NotNull [][] from, int rows, int columns) {
    MutationData[][] newArray = new MutationData[rows][];
    for (int i = 0; i < from.length; i++) {
      if (from[i] == null) continue;
      newArray[i] = new MutationData[columns];
      System.arraycopy(from[i], 0, newArray[i], 0, from[i].length);
    }
    return newArray;
  }

  private int getIncreasedValue(int value) {
    return (int)Math.round(value * myMultiplier);
  }

  private abstract class RowsCounter {
    private final IntSet myRowsSet;
    private int[] myCount;

    RowsCounter() {
      myCount = new int[myMaxRows];
      myRowsSet = new IntOpenHashSet(myMaxRows);
    }

    void shiftUp(@NotNull ModelIndex<GridRow> row) {
      int idx = row.asInteger();
      System.arraycopy(myCount, idx + 1, myCount, idx, myRows - 1 - idx);

      myCount[myRows - 1] = 0;
      myRowsSet.remove(idx);
      IntSet newRowsSet = new IntOpenHashSet();
      for (int oldRowIdx : myRowsSet) {
        if (oldRowIdx > idx) {
          newRowsSet.add(oldRowIdx - 1);
          continue;
        }
        newRowsSet.add(oldRowIdx);
      }
      myRowsSet.clear();
      myRowsSet.addAll(newRowsSet);
    }

    void deleteColumn(@NotNull ModelIndex<GridColumn> idx) {
      for (int i = 0; i < myMaxRows; i++) {
        MutationData oldValue = myValues[i] == null ? null : myValues[i][idx.asInteger()];
        countModifications(null, oldValue, ModelIndex.forRow(myModel, i), idx);
      }
    }

    void deleteRow(@NotNull ModelIndex<GridRow> idx) {
      int i = idx.asInteger();
      if (i < myRows) {
        myCount[i] = 0;
      }
      myRowsSet.remove(i);
    }

    public boolean isEmpty() {
      return myRowsSet.isEmpty();
    }

    public void clear() {
      myCount = new int[myMaxRows];
      myRowsSet.clear();
    }

    public boolean contains(int row) {
      return myRowsSet.contains(row);
    }

    public void countModifications(@Nullable CellMutation value, @Nullable MutationData oldValue, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      myCount[row.asInteger()] += countModificationsInner(value, oldValue, row, column);
      if (myCount[row.asInteger()] > 0) {
        myRowsSet.add(row.asInteger());
        return;
      }
      myRowsSet.remove(row.asInteger());
    }

    protected abstract int countModificationsInner(@Nullable CellMutation value,
                                                   @Nullable MutationData oldValue,
                                                   @NotNull ModelIndex<GridRow> row,
                                                   @NotNull ModelIndex<GridColumn> column);

    public void reallocate() {
      int[] myNewCount = new int[myMaxRows];
      System.arraycopy(myCount, 0, myNewCount, 0, myCount.length);
      myCount = myNewCount;
    }

    public @NotNull IntSet get() {
      return myRowsSet;
    }
  }

  private class ModifiedRowsCounter extends RowsCounter {
    @Override
    protected int countModificationsInner(@Nullable CellMutation value,
                                          @Nullable MutationData oldValue,
                                          @NotNull ModelIndex<GridRow> row,
                                          @NotNull ModelIndex<GridColumn> column) {
      boolean newValueEqualToDatabase = value == null || equalToDatabase(value.getValue(), row, column);
      boolean oldValueEqualToDatabase = oldValue == null || equalToDatabase(oldValue.getValue(), row, column);
      return !newValueEqualToDatabase && oldValueEqualToDatabase ? 1 :
             !oldValueEqualToDatabase && newValueEqualToDatabase ? -1 :
             0;
    }

    boolean equalToDatabase(@Nullable Object value, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
      if (!isValid(row, column) || isInsertedRow(row) || isInsertedColumn(column)) {
        return TypedValue.unwrap(value) == ReservedCellValue.UNSET;
      }
      Object databaseValue = myModel.getValueAt(row, column);
      return ObjectUtils.notNull(databaseValue, ReservedCellValue.NULL) == ObjectUtils.notNull(TypedValue.unwrap(value), ReservedCellValue.NULL);
    }
  }

  private class RowsWithUnparsedValuesCounter extends RowsCounter {
    @Override
    protected int countModificationsInner(@Nullable CellMutation value,
                                          @Nullable MutationData oldValue,
                                          @NotNull ModelIndex<GridRow> row,
                                          @NotNull ModelIndex<GridColumn> column) {
      return unwrap(value) instanceof UnparsedValue && !(unwrap(oldValue) instanceof UnparsedValue) ? 1 :
             unwrap(oldValue) instanceof UnparsedValue && !(unwrap(value) instanceof UnparsedValue) ? -1 :
             0;
    }

    private static @Nullable Object unwrap(@Nullable CellMutation value) {
      return value == null ? null : value.getValue();
    }

    private static @Nullable Object unwrap(@Nullable MutationData value) {
      return value == null ? null : value.getValue();
    }
  }
}