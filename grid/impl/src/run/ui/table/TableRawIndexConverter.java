package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * @author gregsh
 */
public final class TableRawIndexConverter implements RawIndexConverter {

  private final JBTable myTable;
  private final Supplier<Boolean> myIsTransposed;

  public TableRawIndexConverter(@NotNull JBTable table, @NotNull Supplier<Boolean> isTransposed) {
    myTable = table;
    myIsTransposed = isTransposed;
  }

  @Override
  public boolean isValidViewRowIdx(int viewRowIdx) {
    return viewRowIdx >= 0 && viewRowIdx < (myIsTransposed.get() ? myTable.getColumnCount() : myTable.getRowCount());
  }

  @Override
  public boolean isValidViewColumnIdx(int viewColumnIdx) {
    return viewColumnIdx >= 0 && viewColumnIdx < (myIsTransposed.get() ? myTable.getRowCount() : myTable.getColumnCount());
  }

  @Override
  public @NotNull IntUnaryOperator row2View() {
    return index -> {
      if (!isValidModelRowIdx(index)) return -1;
      return myIsTransposed.get() ? myTable.convertColumnIndexToView(index) : myTable.convertRowIndexToView(index);
    };
  }

  @Override
  public @NotNull IntUnaryOperator column2View() {
    return index -> {
      if (!isValidModelColumnIdx(index)) return -1;
      return myIsTransposed.get() ? myTable.convertRowIndexToView(index) : myTable.convertColumnIndexToView(index);
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
      return myIsTransposed.get() ? myTable.convertColumnIndexToModel(index) : myTable.convertRowIndexToModel(index);
    };
  }

  @Override
  public @NotNull IntUnaryOperator column2Model() {
    return index -> {
      if (!isValidViewColumnIdx(index)) return -1;
      return myIsTransposed.get() ? myTable.convertRowIndexToModel(index) : myTable.convertColumnIndexToModel(index);
    };
  }

  private boolean isValidModelRowIdx(int modelRowIdx) {
    return modelRowIdx >= 0 && modelRowIdx < (myIsTransposed.get() ? myTable.getModel().getColumnCount() : myTable.getModel().getRowCount());
  }

  private boolean isValidModelColumnIdx(int modelColumnIdx) {
    return modelColumnIdx >= 0 && modelColumnIdx < (myIsTransposed.get() ? myTable.getModel().getRowCount() : myTable.getModel().getColumnCount());
  }
}
