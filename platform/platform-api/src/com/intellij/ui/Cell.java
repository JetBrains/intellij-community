/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

public class Cell {
  public final int row;
  public final int column;

  public Cell(int row, int column) {
    this.row = row;
    this.column = column;
  }

  public int getRow() {
    return row;
  }

  public int getColumn() {
    return column;
  }

  @Override
  public String toString() {
    return row + "@" + column;
  }

  @Override
  public int hashCode() {
    return row * 31 + column;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) return false;
    if (o.getClass() != getClass()) return false;
    return ((Cell)o).row == row && ((Cell)o).column == column;
  }
}
