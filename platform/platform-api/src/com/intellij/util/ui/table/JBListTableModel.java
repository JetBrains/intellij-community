/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.ui.table;

import com.intellij.util.ui.EditableModel;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBListTableModel extends AbstractTableModel implements EditableModel {
  private final JTable myTable;

  public JBListTableModel(JTable table) {
    myTable = table;
  }

  @Override
  public int getRowCount() {
    return myTable.getRowCount();
  }

  @Override
  public final int getColumnCount() {
    return 1;
  }

  @Override
  public String getColumnName(int columnIndex) {
    return "";
  }


  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return true;
  }

  public abstract JBTableRow getRow(int index);

  @Override
  public final JBTableRow getValueAt(int rowIndex, int columnIndex) {
    return getRow(rowIndex);
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
  }

  @Override
  public void addRow() {
    final TableModel model = myTable.getModel();
    if (model instanceof EditableModel) {
      ((EditableModel)model).addRow();
    }
  }

  @Override
  public void removeRow(int index) {
    final TableModel model = myTable.getModel();
    if (model instanceof EditableModel) {
      ((EditableModel)model).removeRow(index);
    }
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {
    final TableModel model = myTable.getModel();
    if (model instanceof EditableModel) {
      ((EditableModel)model).exchangeRows(oldIndex, newIndex);
    }
  }
}
