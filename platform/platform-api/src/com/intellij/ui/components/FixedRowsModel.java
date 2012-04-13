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
package com.intellij.ui.components;

import javax.swing.*;

/**
* @author Konstantin Bulenkov
*/
class FixedRowsModel extends MultiColumnListModel {
  private final int myMaxRows;

  public FixedRowsModel(ListModel model, int rows) {
    super(model);
    myMaxRows = rows;
  }

  @Override
  public int getRowCount() {
    return Math.min(myMaxRows, getSize());
  }

  @Override
  public int getColumnCount() {
    final int rows = getRowCount();
    return rows == 0 ? 0 : getSize() / rows + 1;
  }

  @Override
  public int toListIndex(int row, int column) {
    final int rows = getRowCount();
    return rows == 0 ? -1 : column * rows + row;
  }

  @Override
  public int getColumn(int listIndex) {
    return listIndex / myMaxRows;
  }

  @Override
  public int getRow(int listIndex) {
    return listIndex % myMaxRows;
  }
}
