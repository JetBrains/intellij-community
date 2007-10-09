/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dyoma
 */
public class FilteringListModel<T> extends AbstractListModel {
  private final ListModel myOriginalModel;
  private Condition<T> myCondition = null;
  private final ListDataListener myListDataListener = new ListDataListener() {
        public void contentsChanged(ListDataEvent e) {
          int[] interval = findFilteredInterval(e);
          fireContentsChanged(this, filteredIndex(interval[0]), filteredIndex(interval[1]));
        }

        public void intervalAdded(ListDataEvent e) {
          int[] interval = findFilteredInterval(e);
          fireIntervalAdded(this, interval[0], interval[1]);
        }

        public void intervalRemoved(ListDataEvent e) {
          int[] interval = findFilteredInterval(e);
          fireIntervalRemoved(this, interval[0], interval[1]);
        }
      };

  public FilteringListModel(ListModel originalModel) {
    myOriginalModel = originalModel;
    myOriginalModel.addListDataListener(myListDataListener);
  }

  public void dispose() {
    myOriginalModel.removeListDataListener(myListDataListener);
  }

  public void setFilter(Condition<T> condition) {
    int prevSize = getSize();
    myCondition = condition;
    int newSize = getSize();
    fireContentsChanged(this, 0, Math.min(prevSize, newSize) - 1);
    if (newSize > prevSize) fireIntervalAdded(this, prevSize, newSize - 1);
    else if (newSize < prevSize) fireIntervalRemoved(this, newSize, prevSize - 1);
  }

  private int[] findFilteredInterval(ListDataEvent e) {
    return findFilteredInterval(e.getIndex0(), e.getIndex1());
  }

  public int getSize() {
    return getItems().size();
  }

  public Object getElementAt(int index) {
    return getItems().get(index);
  }

  private List<T> getItems() {
    ArrayList<T> list = new ArrayList<T>();
    for (int i = 0; i < myOriginalModel.getSize(); i++) {
      T element = getOriginalElementAt(i);
      if (passElement(element)) list.add(element);
    }
    return list;
  }

  private T getOriginalElementAt(int i) {
    return (T)myOriginalModel.getElementAt(i);
  }

  private boolean passElement(T element) {
    return myCondition == null || myCondition.value(element);
  }

  private int findFirst(int fromIndex, int toIndex) {
    for (int i = fromIndex; i <= toIndex; i ++) {
      if (passElement(getOriginalElementAt(i))) return i;
    }
    return -1;
  }

  private int findLast(int fromIndex, int toIndex) {
    for (int i = toIndex; i >= fromIndex; i--) {
      if (passElement(getOriginalElementAt(i))) return i;
    }
    return -1;
  }

  private int filteredIndex(int index) {
    return getItems().indexOf(getOriginalElementAt(index));
  }

  private int[] findFilteredInterval(int index0, int index1) {
    int[] interval = new int[2];
    interval[0] = findFirst(index0, index1);
    if (interval[0] == -1) return null;
    interval[1] = findLast(interval[0], index1);
    return interval;
  }

  private int getOriginalIndexOf(T item) {
    for (int i = 0; i < myOriginalModel.getSize(); i++)
      if (Comparing.equal(item, myOriginalModel.getElementAt(i))) return i;
    return -1;
  }

  public int findNearestIndex(T item) {
    int index = getItems().indexOf(item);
    if (index != -1) return index;
    int originalIndex = getOriginalIndexOf(item);
    if (originalIndex == -1) return -1;
    index = findFirst(originalIndex, myOriginalModel.getSize() - 1);
    if (index != -1) return filteredIndex(index);
    index = findLast(0, originalIndex);
    if (index != -1) return filteredIndex(index);
    return -1;
  }
}
