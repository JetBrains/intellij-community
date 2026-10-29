package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumnModel;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * @author gregsh
 */
public final class TableRawIndexConverter implements RawIndexConverter {

  private final JBTable myTable;
  /** The columns to convert, or null for the table's own column model, which the table may replace. */
  private final @Nullable TableColumnModel myColumns;
  private final Supplier<Boolean> myIsTransposed;

  /**
   * Model-to-view column index cache, EDT only. {@link JBTable#convertColumnIndexToView} scans the whole column model,
   * so converting every index is quadratic; this is rebuilt in one pass and then answers in O(1). Model indices are
   * assigned at construction, so only structural changes invalidate it - not the frequent width changes.
   */
  private int @Nullable [] myModelToViewColumn;
  private @Nullable TableColumnModel myObservedColumnModel;

  private final TableColumnModelListener myColumnModelListener = new TableColumnModelListener() {
    @Override
    public void columnAdded(TableColumnModelEvent e) {
      myModelToViewColumn = null;
    }

    @Override
    public void columnRemoved(TableColumnModelEvent e) {
      myModelToViewColumn = null;
    }

    @Override
    public void columnMoved(TableColumnModelEvent e) {
      // DefaultTableColumnModel posts this even when a drag has not crossed a column boundary.
      if (e.getFromIndex() != e.getToIndex()) myModelToViewColumn = null;
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) { }

    @Override
    public void columnSelectionChanged(ListSelectionEvent e) { }
  };

  public TableRawIndexConverter(@NotNull JBTable table, @NotNull Supplier<Boolean> isTransposed) {
    myTable = table;
    myColumns = null;
    myIsTransposed = isTransposed;
  }

  /** Converts the rows and the columns of {@code table}, which may replace its column model. */
  TableRawIndexConverter(@NotNull GridTable table) {
    myTable = table;
    myColumns = null;
    myIsTransposed = table::isTransposed;
  }

  /** Converts the rows of {@code table} and the columns of {@code columns}, which may hold more columns than the table shows. */
  TableRawIndexConverter(@NotNull GridTable table, @NotNull TableColumnModel columns) {
    myTable = table;
    myColumns = columns;
    myIsTransposed = table::isTransposed;
  }

  private @NotNull TableColumnModel columns() {
    return myColumns != null ? myColumns : myTable.getColumnModel();
  }

  @Override
  public boolean isValidViewRowIdx(int viewRowIdx) {
    return viewRowIdx >= 0 && viewRowIdx < (myIsTransposed.get() ? columns().getColumnCount() : myTable.getRowCount());
  }

  @Override
  public boolean isValidViewColumnIdx(int viewColumnIdx) {
    return viewColumnIdx >= 0 && viewColumnIdx < (myIsTransposed.get() ? myTable.getRowCount() : columns().getColumnCount());
  }

  @Override
  public @NotNull IntUnaryOperator row2View() {
    return index -> {
      if (!isValidModelRowIdx(index)) return -1;
      return myIsTransposed.get() ? columnIndexToView(index) : myTable.convertRowIndexToView(index);
    };
  }

  @Override
  public @NotNull IntUnaryOperator column2View() {
    return index -> {
      if (!isValidModelColumnIdx(index)) return -1;
      return myIsTransposed.get() ? myTable.convertRowIndexToView(index) : columnIndexToView(index);
    };
  }

  @Override
  public @NotNull PairPairFunction<Integer> rowAndColumn2Model() {
    return (row, column) -> new Pair<>(row2Model().applyAsInt(row), column2Model().applyAsInt(column));
  }

  @Override
  public @NotNull PairPairFunction<Integer> rowAndColumn2View() {
    return (row, column) -> new Pair<>(row2View().applyAsInt(row), column2View().applyAsInt(column));
  }

  @Override
  public @NotNull IntUnaryOperator row2Model() {
    return index -> {
      if (!isValidViewRowIdx(index)) return -1;
      return myIsTransposed.get() ? columnIndexToModel(index) : myTable.convertRowIndexToModel(index);
    };
  }

  @Override
  public @NotNull IntUnaryOperator column2Model() {
    return index -> {
      if (!isValidViewColumnIdx(index)) return -1;
      return myIsTransposed.get() ? myTable.convertRowIndexToModel(index) : columnIndexToModel(index);
    };
  }

  private int columnIndexToModel(int viewColumnIdx) {
    return columns().getColumn(viewColumnIdx).getModelIndex();
  }

  /** The position of model column {@code modelColumnIdx} in the converted columns, or -1; from a map that only a change drops. */
  int columnIndexToView(int modelColumnIdx) {
    int[] map = modelToViewColumn();
    return modelColumnIdx >= 0 && modelColumnIdx < map.length ? map[modelColumnIdx] : -1;
  }

  private int @NotNull [] modelToViewColumn() {
    TableColumnModel columnModel = columns();
    if (columnModel != myObservedColumnModel) {
      observeColumnModel(columnModel);
    }
    int modelColumnCount = myTable.getModel().getColumnCount();
    int[] map = myModelToViewColumn;
    if (map != null && map.length == modelColumnCount) return map;

    map = new int[modelColumnCount];
    Arrays.fill(map, -1);
    for (int viewIdx = 0; viewIdx < columnModel.getColumnCount(); viewIdx++) {
      int modelIdx = columnModel.getColumn(viewIdx).getModelIndex();
      if (modelIdx >= 0 && modelIdx < map.length && map[modelIdx] == -1) map[modelIdx] = viewIdx; // first match wins, as in JTable
    }
    myModelToViewColumn = map;
    return map;
  }

  private void observeColumnModel(@NotNull TableColumnModel columnModel) {
    if (myObservedColumnModel != null) myObservedColumnModel.removeColumnModelListener(myColumnModelListener);
    columnModel.addColumnModelListener(myColumnModelListener);
    myObservedColumnModel = columnModel;
    myModelToViewColumn = null;
  }

  private boolean isValidModelRowIdx(int modelRowIdx) {
    return modelRowIdx >= 0 && modelRowIdx < (myIsTransposed.get() ? myTable.getModel().getColumnCount() : myTable.getModel().getRowCount());
  }

  private boolean isValidModelColumnIdx(int modelColumnIdx) {
    return modelColumnIdx >= 0 && modelColumnIdx < (myIsTransposed.get() ? myTable.getModel().getRowCount() : myTable.getModel().getColumnCount());
  }
}
