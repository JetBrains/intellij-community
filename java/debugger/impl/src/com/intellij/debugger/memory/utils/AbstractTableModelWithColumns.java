/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.utils;

import javax.swing.table.AbstractTableModel;

public abstract class AbstractTableModelWithColumns extends AbstractTableModel {

  private final TableColumnDescriptor[] myColumnDescriptors;
  public AbstractTableModelWithColumns(TableColumnDescriptor[] descriptors) {
    myColumnDescriptors = descriptors;
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return myColumnDescriptors[columnIndex].getColumnClass();
  }

  @Override
  public String getColumnName(int column) {
    return myColumnDescriptors[column].getName();
  }

  @Override
  public int getColumnCount() {
    return myColumnDescriptors.length;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return myColumnDescriptors[columnIndex].getValue(rowIndex);
  }

  interface TableColumnDescriptor {
    Class<?> getColumnClass();
    Object getValue(int ix);
    String getName();
  }
}
