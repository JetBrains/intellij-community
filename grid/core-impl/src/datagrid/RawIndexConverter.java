package com.intellij.database.datagrid;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntUnaryOperator;

/**
 * @author gregsh
 */
public interface RawIndexConverter {

  boolean isValidViewRowIdx(int viewRowIdx);

  boolean isValidViewColumnIdx(int viewColumnIdx);

  IntUnaryOperator row2View();

  IntUnaryOperator column2View();

  @NotNull
  PairPairFunction<Integer> rowAndColumn2Model();

  @NotNull
  PairPairFunction<Integer> rowAndColumn2View();

  IntUnaryOperator row2Model();

  IntUnaryOperator column2Model();

  @FunctionalInterface
  interface PairPairFunction <T> {
    Pair<T, T> fun(T v1, T v2);
  }
}
