package com.intellij.database.datagrid;

import com.intellij.util.containers.JBIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;

/**
 * @author Liudmila Kornilova
 **/
public interface GridRow extends Iterable<Object> {
  @Nullable
  Object getValue(int columnNum);

  int getSize();

  void setValue(int i, @Nullable Object object);

  @Override
  default @NotNull Iterator<Object> iterator() {
    return new JBIterator<>() {
      private int myNextValueIdx;

      @Override
      protected Object nextImpl() {
        return myNextValueIdx < getSize() ? getValue(myNextValueIdx++) : stop();
      }
    };
  }

  static Object @NotNull [] getValues(@NotNull GridRow row) {
    Object[] v = new Object[row.getSize()];
    for (int i = 0; i < row.getSize(); i++) {
      v[i] = row.getValue(i);
    }
    return v;
  }

  int getRowNum();

  static int toRealIdx(@NotNull GridRow row) {
    return row.getRowNum() - 1;
  }

  static boolean equals(GridRow row1, GridRow row2) {
    if (row1 == row2) return true;
    if (row1 == null || row2 == null) return false;
    if (row1.getRowNum() != row2.getRowNum()) return false;
    if (row1.getSize() != row2.getSize()) return false;
    for (int i = 0; i < row1.getSize(); i++) {
      if (!Objects.equals(row1.getValue(i), row2.getValue(i))) {
        return false;
      }
    }
    return true;
  }
}
