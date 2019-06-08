/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.speedSearch;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.Condition;

import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.List;

public class FilteringTableModel<T> extends AbstractTableModel {
  private final TableModel myOriginalModel;
  private final List<List<T>> myData = new ArrayList<>();
  private final Class<T> myClz;
  private Condition<? super T> myCondition = null;
  private final ArrayList<Integer> myIndex = new ArrayList<>();

  private final TableModelListener myListDataListener = e -> refilter();

  public FilteringTableModel(TableModel originalModel, Class<T> clz) {
    myClz = clz;
    myOriginalModel = originalModel;
    myOriginalModel.addTableModelListener(myListDataListener);
  }

  public void dispose() {
    myOriginalModel.removeTableModelListener(myListDataListener);
  }

  public void setFilter(Condition<? super T> condition) {
    myCondition = condition;
    refilter();
  }

  private void removeAllElements() {
    int index1 = myData.size() - 1;
    if (index1 >= 0) {
      myData.clear();
      fireTableRowsDeleted(0, index1);
    }
    myIndex.clear();
  }

  public void refilter() {
    removeAllElements();
    int count = 0;
    for (int i = 0; i < myOriginalModel.getRowCount(); i++) {
      Object valueAt = myOriginalModel.getValueAt(i, 0);
      boolean include = false;
      if (valueAt != null && myClz.isAssignableFrom(valueAt.getClass())) {
        //noinspection unchecked
        final T element = (T)valueAt;
        if (passElement(element)) {
          include = true;
        }
      } else {
        include = true;
      }
      if (include) {
        List<T> elements = Lists.newArrayListWithCapacity(myOriginalModel.getColumnCount());
        for (int col = 0; col < myOriginalModel.getColumnCount(); col++) {
          //noinspection unchecked
          elements.add((T)myOriginalModel.getValueAt(i, col));
        }
        addToFiltered(elements);
        myIndex.add(i);
        count++;
      }
    }

    if (count > 0) {
      fireTableRowsInserted(0, count - 1);
    }
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return myOriginalModel.isCellEditable(myIndex.get(rowIndex), columnIndex);
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    myOriginalModel.setValueAt(aValue, myIndex.get(rowIndex), columnIndex);
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return myOriginalModel.getColumnClass(columnIndex);
  }

  @Override
  public String getColumnName(int column) {
    return myOriginalModel.getColumnName(column);
  }

  protected void addToFiltered(List<T> elt) {
    myData.add(elt);
  }

  public int getSize() {
    return myData.size();
  }

  private boolean passElement(T element) {
    return myCondition == null || myCondition.value(element);
  }

  @Override
  public int getRowCount() {
    return myData.size();
  }

  @Override
  public int getColumnCount() {
    return myOriginalModel.getColumnCount();
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    if (rowIndex >= myData.size() || rowIndex < 0 || columnIndex < 0 || columnIndex >= getColumnCount()) {
      return null;
    }
    return myData.get(rowIndex).get(columnIndex);
  }
}
