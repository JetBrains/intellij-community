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
package com.intellij.ui;

import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.util.*;

public class SortedListModel<T> extends AbstractListModel<T> {
  private List<T> myItems = new ArrayList<>();
  private final Comparator<T> myComparator;

  public SortedListModel(Comparator<T> comparator) {
    myComparator = comparator;
  }

  public static <T> SortedListModel<T> create(Comparator<T> comparator) {
    return new SortedListModel<>(comparator);
  }

  public int add(T item) {
    int index;
    if (myComparator != null) {
      index = Collections.binarySearch(myItems, item, myComparator);
    }
    else {
      index = myItems.size();
    }
    index = index >= 0 ? index : -(index + 1);
    add(index, item);
    return index;
  }

  public int[] addAll(T[] items) {
    return addAll(Arrays.asList(items));
  }

  public int[] addAll(Iterator<T> iterator) {
    return addAll(ContainerUtil.collect(iterator));
  }

  public int[] addAll(Collection<T> items) {
    int[] indices = new int[items.size()];
    int i = 0;
    for (T item : items) {
      int newIndex = add(item);
      for (int j = 0; j < i; j++) {
        if (indices[j] >= newIndex) indices[j]++;
      }
      indices[i] = newIndex;
      i++;
    }
    return indices;
  }

  public void remove(int index) {
    myItems.remove(index);
    fireRemoved(index);
  }

  private void fireRemoved(int index) {
    fireIntervalRemoved(this, index, index);
  }

  public void remove(T item) {
    int index = indexOf(item);
    if (index >= 0) remove(index);
  }

  public int indexOf(T item) {
    int index = Collections.binarySearch(myItems, item, myComparator);
    return index >= 0 ? index : -1;
  }

  private void add(int index, T item) {
    myItems.add(index, item);
    fireIntervalAdded(this, index, index);
  }

  @Override
  public int getSize() {
    return myItems.size();
  }

  @Override
  public T getElementAt(int index) {
    return myItems.get(index);
  }

  public void setAll(Collection<T> items) {
    clear();
    myItems.addAll(items);
    if (myComparator != null) Collections.sort(myItems, myComparator);
    int newSize = getSize();
    if (newSize > 0) fireIntervalAdded(this, 0, newSize - 1);
  }

  public void clear() {
    int oldSize = getSize();
    myItems = new ArrayList<>();
    if (oldSize > 0) fireIntervalRemoved(this, 0, oldSize - 1);
  }

  public List<T> getItems() {
    return myItems;
  }

  public T get(int index) {
    return myItems.get(index);
  }

  public void setAll(T[] items) {
    setAll(Arrays.asList(items));
  }

  public Iterator<T> iterator() {
    return new MyIterator();
  }

  private class MyIterator implements Iterator<T> {
    private final Iterator<T> myIterator;
    private int myCounter = -1;

    public MyIterator() {
      myIterator = myItems.iterator();
    }

    @Override
    public boolean hasNext() {
      return myIterator.hasNext();
    }

    @Override
    public T next() {
      myCounter++;
      return myIterator.next();
    }

    @Override
    public void remove() {
      myIterator.remove();
      fireRemoved(myCounter);
      myCounter--;
    }
  }
}
