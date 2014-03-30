/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class CollectionListModel<T> extends AbstractListModel implements EditableModel {
  private final List<T> myItems;

  public CollectionListModel(@NotNull final Collection<? extends T> items) {
    myItems = new ArrayList<T>(items);
  }

  public CollectionListModel(@NotNull final List<? extends T> items) {
    this((Collection<? extends T>)items);
  }

  public CollectionListModel(final T... items) {
    myItems = ContainerUtilRt.newArrayList(items);
  }

  @Override
  public int getSize() {
    return myItems.size();
  }

  @Override
  public T getElementAt(final int index) {
    return myItems.get(index);
  }

  public void add(final T element) {
    int i = myItems.size();
    myItems.add(element);
    fireIntervalAdded(this, i, i);
  }

  public void add(int i,final T element) {
    myItems.add(i, element);
    fireIntervalAdded(this, i, i);
  }

  public void add(@NotNull final List<? extends T> elements) {
    if (elements.isEmpty()) return;
    int i = myItems.size();
    myItems.addAll(elements);
    fireIntervalAdded(this, i, i + elements.size() - 1);
  }

  public void remove(@NotNull final T element) {
    int i = myItems.indexOf(element);
    myItems.remove(element);
    fireIntervalRemoved(this, i, i);
  }

  public void setElementAt(@NotNull final T element, final int index) {
    myItems.set(index, element);
    fireContentsChanged(this, index, index);
  }

  public void remove(final int index) {
    myItems.remove(index);
    fireIntervalRemoved(this, index, index);
  }

  public void removeAll() {
    int size = myItems.size();
    if (size > 0) {
      myItems.clear();
      fireIntervalRemoved(this, 0, size - 1);
    }
  }

  public void contentsChanged(@NotNull final T element) {
    int i = myItems.indexOf(element);
    fireContentsChanged(this, i, i);
  }

  public void sort(final Comparator<T> comparator) {
    Collections.sort(myItems, comparator);
  }

  public List<T> getItems() {
    return Collections.unmodifiableList(myItems);
  }

  public void replaceAll(@NotNull final List<? extends T> elements) {
    removeAll();
    add(elements);
  }

  @Override
  public void addRow() {
  }

  @Override
  public void removeRow(int index) {
    remove(index);
  }

  @Override
  public void exchangeRows(int oldIndex, int newIndex) {
    Collections.swap(myItems, oldIndex, newIndex);
    fireContentsChanged(this, oldIndex, oldIndex);
    fireContentsChanged(this, newIndex, newIndex);
  }

  @Override
  public boolean canExchangeRows(int oldIndex, int newIndex) {
    return true;
  }

  @NonNls
  @Override
  public String toString() {
    return getClass().getName() + " (" + getSize() + " elements)";
  }

  public List<T> toList() {
    return new ArrayList<T>(myItems);
  }

  public int getElementIndex(T item) {
    return myItems.indexOf(item);
  }
}
