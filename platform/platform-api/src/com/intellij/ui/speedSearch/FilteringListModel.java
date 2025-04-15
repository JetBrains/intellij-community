// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.speedSearch;

import com.intellij.openapi.util.Condition;
import com.intellij.ui.ListUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FilteringListModel<T> extends AbstractListModel<T> {
  private final ListModel<T> myOriginalModel;
  private final List<T> myData = new ArrayList<>();
  private Condition<? super T> myCondition = null;
  private boolean myUpdating = false;

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

  public void refilter() {
    if (myUpdating) return;
    List<T> newData = new ArrayList<>();
    Collection<T> elements = getElementsToFilter();
    for (T elt : elements) {
      if (passElement(elt)) {
        newData.add(elt);
      }
    }
    
    commit(newData);
  }

  private void commit(List<T> newData) {
    Diff.Change change;
    try {
      change = Diff.buildChanges(myData.toArray(), newData.toArray(), HashingStrategy.identity());
    }
    catch (FilesTooBigForDiffException e) {
      replace(0, myData.size(), newData);
      return;
    }
    if (change != null) {
      ArrayList<Diff.Change> list = change.toList();
      Collections.reverse(list);
      for (Diff.Change ch : list) {
        replace(ch.line0, ch.line0 + ch.deleted, newData.subList(ch.line1, ch.line1 + ch.inserted));
      }
      assert myData.equals(newData);
    }
  }

  /**
   * Replaces the interval between from and to with elements from the new list.
   * 
   * @param from start index
   * @param to end index
   * @param newData new data
   */
  protected void replace(int from, int to, List<T> newData) {
    if (to > from) {
      myData.subList(from, to).clear();
      fireIntervalRemoved(this, from, to - 1);
    }
    if (!newData.isEmpty()) {
      myData.addAll(from, newData);
      fireIntervalAdded(this, from, from + newData.size() - 1);
    }
  }

  protected @NotNull @Unmodifiable Collection<T> getElementsToFilter() {
    List<T> result = new ArrayList<>(myOriginalModel.getSize());
    for (int i = 0; i < myOriginalModel.getSize(); i++) {
      result.add(myOriginalModel.getElementAt(i));
    }
    return result;
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

  public @NotNull ListModel<T> getOriginalModel() {
    return myOriginalModel;
  }

  public void addAll(List<? extends T> elements) {
    ListUtil.addAllItems(myOriginalModel, elements);
  }

  public void replaceAll(List<? extends T> elements) {
    try {
      myUpdating = true;
      ListUtil.removeAllItems(myOriginalModel);
      ListUtil.addAllItems(myOriginalModel, elements);
    }
    finally {
      myUpdating = false;
      refilter();
    }
  }

  public void remove(int index) {
    ListUtil.removeItem(myOriginalModel, index);
  }
}
