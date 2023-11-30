// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @Nullable Object getValueAt(int row, int column) {
    final int index = toListIndex(row, column);
    return index == -1 || index >= myModel.getSize() ? null
                                                     : myModel.getElementAt(index);
  }
}
