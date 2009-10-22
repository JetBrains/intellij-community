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

/**
 * @author max
 */
public class FilteringListModel<T> extends DefaultListModel {
  private final JList myList;
  private final ListModel myOriginalModel;
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

  public void refilter() {
    removeAllElements();
    for (int i = 0; i < myOriginalModel.getSize(); i++) {
      final T elt = (T)myOriginalModel.getElementAt(i);
      if (passElement(elt)) {
        addToFiltered(elt);
      }
    }
  }

  protected void addToFiltered(T elt) {
    addElement(elt);
  }

  private boolean passElement(T element) {
    return myCondition == null || myCondition.value(element);
  }
}
