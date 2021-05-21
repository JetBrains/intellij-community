// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.speedSearch;

import com.intellij.openapi.util.Condition;
import com.intellij.ui.ListUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FilteringListModel<T> extends AbstractListModel<T> {
  private final ListModel<T> myOriginalModel;
  private final List<T> myData = new ArrayList<>();
  private Condition<? super T> myCondition = null;

  private final ListDataListener myListDataListener = new ListDataListener() {
    @Override
    public void contentsChanged(ListDataEvent e) {
      refilter();
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
      refilter();
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
      refilter();
    }
  };

  public FilteringListModel(ListModel<T> originalModel) {
    myOriginalModel = originalModel;
    myOriginalModel.addListDataListener(myListDataListener);
  }

  public void dispose() {
    myOriginalModel.removeListDataListener(myListDataListener);
  }

  public void setFilter(Condition<? super T> condition) {
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
    Collection<T> elements = getElementsToFilter();
    for (T elt : elements) {
      if (passElement(elt)) {
        addToFiltered(elt);
        count++;
      }
    }

    if (count > 0) {
      fireIntervalAdded(this, 0, count - 1);
    }
  }

  @NotNull
  protected Collection<T> getElementsToFilter() {
    ArrayList<T> result = new ArrayList<>();
    for (int i = 0; i < myOriginalModel.getSize(); i++) {
      result.add(myOriginalModel.getElementAt(i));
    }
    return result;
  }

  protected void addToFiltered(T elt) {
    myData.add(elt);
  }

  @Override
  public int getSize() {
    return myData.size();
  }

  @Override
  public T getElementAt(int index) {
    return myData.get(index);
  }

  public int getElementIndex(T element) {
    return myData.indexOf(element);
  }

  private boolean passElement(T element) {
    return myCondition == null || myCondition.value(element);
  }

  public boolean contains(T value) {
    return myData.contains(value);
  }

  @NotNull
  public ListModel<T> getOriginalModel() {
    return myOriginalModel;
  }

  public void addAll(List<? extends T> elements) {
    ListUtil.addAllItems(myOriginalModel, elements);
  }

  public void replaceAll(List<? extends T> elements) {
    ListUtil.removeAllItems(myOriginalModel);
    ListUtil.addAllItems(myOriginalModel, elements);
  }

  public void remove(int index) {
    ListUtil.removeItem(myOriginalModel, index);
  }
}
