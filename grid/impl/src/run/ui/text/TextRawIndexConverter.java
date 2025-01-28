package com.intellij.database.run.ui.text;

import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntUnaryOperator;

public final class TextRawIndexConverter implements RawIndexConverter {

  @Override
  public boolean isValidViewRowIdx(int viewRowIdx) {
    return false;
  }

  @Override
  public boolean isValidViewColumnIdx(int viewColumnIdx) {
    return false;
  }

  @Override
  public @NotNull IntUnaryOperator row2View() {
    return index -> {
      return -1;
    };
  }

  @Override
  public @NotNull IntUnaryOperator column2View() {
    return index -> {
      return -1;
    };
  }

  @Override
  public @NotNull PairPairFunction<Integer> rowAndColumn2Model() {
    return (row, column) -> {
      return new Pair<>(-1, -1);
    };
  }

  @Override
  public @NotNull PairPairFunction<Integer> rowAndColumn2View() {
    return (row, column) -> {
      return new Pair<>(-1, -1);
    };
  }

  @Override
  public @NotNull IntUnaryOperator row2Model() {
    return index -> {
      return -1;
    };
  }

  @Override
  public @NotNull IntUnaryOperator column2Model() {
    return index -> {
      return -1;
    };
  }
}
