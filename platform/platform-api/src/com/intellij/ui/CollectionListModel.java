/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.ui;

import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author yole
 */
public class CollectionListModel<T> extends AbstractListModel<T> implements EditableModel {
  private final List<T> myItems;

  public CollectionListModel(@NotNull final Collection<? extends T> items) {
    myItems = new ArrayList<>(items);
  }

  @SuppressWarnings("UnusedParameters")
  public CollectionListModel(@NotNull List<T> items, boolean useListAsIs) {
    myItems = items;
  }

  public CollectionListModel(@NotNull final List<? extends T> items) {
    myItems = new ArrayList<>(items);
  }

  @SafeVarargs
  public CollectionListModel(@NotNull T... items) {
    myItems = ContainerUtilRt.newArrayList(items);
  }

  @NotNull
  protected final List<T> getInternalList() {
    return myItems;
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
    addAll(myItems.size(), elements);
  }

  public void addAll(int index, @NotNull final List<? extends T> elements) {
    if (elements.isEmpty()) return;

    myItems.addAll(index, elements);
    fireIntervalAdded(this, index, index + elements.size() - 1);
  }

  public void remove(@NotNull T element) {
    int index = getElementIndex(element);
    if (index != -1) {
      remove(index);
    }
  }

  public void setElementAt(@NotNull final T item, final int index) {
    itemReplaced(myItems.set(index, item), item);
    fireContentsChanged(this, index, index);
  }

  @SuppressWarnings("UnusedParameters")
  protected void itemReplaced(@NotNull T existingItem, @Nullable T newItem) {
  }

  public void remove(final int index) {
    T item = myItems.remove(index);
    if (item != null) {
      itemReplaced(item, null);
    }
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

  @NotNull
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
    return new ArrayList<>(myItems);
  }

  public int getElementIndex(T item) {
    return myItems.indexOf(item);
  }

  public boolean isEmpty() {
    return myItems.isEmpty();
  }

  public boolean contains(T item) {
    return getElementIndex(item) >= 0;
  }

  public void removeRange(int fromIndex, int toIndex) {
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex must be <= toIndex");
    }
    for(int i = toIndex; i >= fromIndex; i--) {
      itemReplaced(myItems.remove(i), null);
    }
    fireIntervalRemoved(this, fromIndex, toIndex);
  }

}
