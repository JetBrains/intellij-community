// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

public final class TableCell {
  public final int row;
  public final int column;

  public TableCell(int rowIndex, int columnIndex) {
    row = rowIndex;
    column = columnIndex;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TableCell)) return false;

    final TableCell myKey = (TableCell)o;

    if (column != myKey.column) return false;
    if (row != myKey.row) return false;

    return true;
  }

  public boolean at(int row, int column) {
    return row == this.row && column == this.column;
  }

  @Override public int hashCode() {
    int result;
    result = row;
    result = 29 * result + column;
    return result;
  }
}
