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

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

/**
 * @author Konstantin Bulenkov
 */
public abstract class MultiColumnListModel extends AbstractTableModel implements TableModel {
  private final ListModel myModel;

  public MultiColumnListModel(ListModel model) {
    myModel = model;
  }

  public abstract int toListIndex(int row, int column);
  public abstract int getColumn(int listIndex);
  public abstract int getRow(int listIndex);

  public int getSize() {
    return myModel.getSize();
  }

  @Nullable
  @Override
  public Object getValueAt(int row, int column) {
    final int index = toListIndex(row, column);
    return index == -1 || index >= myModel.getSize() ? null
                                                     : myModel.getElementAt(index);
  }
}
