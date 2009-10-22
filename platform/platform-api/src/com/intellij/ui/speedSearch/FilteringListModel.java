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

/*
 * @author max
 */
package com.intellij.ui.speedSearch;

import com.intellij.openapi.util.Condition;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class FilteringListModel<T> extends AbstractListModel {
  private final JList myList;
  private final ListModel myOriginalModel;
  private final List<T> myData = new ArrayList<T>();
  private Condition<T> myCondition = null;


  private final ListDataListener myListDataListener = new ListDataListener() {
    public void contentsChanged(ListDataEvent e) {
      refilter();
    }

    public void intervalAdded(ListDataEvent e) {
      refilter();
    }

    public void intervalRemoved(ListDataEvent e) {
      refilter();
    }
  };

  protected FilteringListModel(JList list) {
    myList = list;
    myOriginalModel = list.getModel();
    myOriginalModel.addListDataListener(myListDataListener);

    list.setModel(this);
  }

  public void dispose() {
    myOriginalModel.removeListDataListener(myListDataListener);
  }

  public void setFilter(Condition<T> condition) {
    myCondition = condition;
    refilter();
  }

  private void removeAllElements() {
    int index1 = myData.size() - 1;
    if (index1 >= 0) {
      myData.clear();
      fireIntervalRemoved(this, 0, index1);
    }
  }

  public void refilter() {
    removeAllElements();
    int count = 0;
    for (int i = 0; i < myOriginalModel.getSize(); i++) {
      final T elt = (T)myOriginalModel.getElementAt(i);
      if (passElement(elt)) {
        addToFiltered(elt);
        count++;
      }
    }

    if (count > 0) {
      fireIntervalAdded(this, 0, count - 1);
    }
  }

  protected void addToFiltered(T elt) {
    myData.add(elt);
  }

  public int getSize() {
    return myData.size();
  }

  public Object getElementAt(int index) {
    return myData.get(index);
  }

  private boolean passElement(T element) {
    return myCondition == null || myCondition.value(element);
  }

  public boolean contains(T value) {
    return myData.contains(value);
  }
}
