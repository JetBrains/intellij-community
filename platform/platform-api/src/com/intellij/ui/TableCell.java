/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

public final class TableCell {
  public final int row;
  public final int column;

  public TableCell(int rowIndex, int columnIndex) {
    row = rowIndex;
    column = columnIndex;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TableCell)) return false;

    final TableCell myKey = (TableCell)o;

    if (column != myKey.column) return false;
    if (row != myKey.row) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = row;
    result = 29 * result + column;
    return result;
  }
}
