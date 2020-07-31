// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class MutableCollectionComboBoxModel<T> extends CollectionComboBoxModel<T> implements MutableComboBoxModel<T> {

  public MutableCollectionComboBoxModel(@NotNull List<T> items) {
    super(items);
  }

  public MutableCollectionComboBoxModel() {
    super();
  }

  public MutableCollectionComboBoxModel(@NotNull List<T> items, @Nullable T selection) {
    super(items, selection);
  }

  public void update(@NotNull List<? extends T> items) {
    replaceAll(items);
  }

  public void addItem(T item) {
    add(item);
  }

  @Override
  protected final void fireIntervalAdded(Object source, int index0, int index1) {
    super.fireIntervalAdded(source, index0, index1);

    if (getSize() == 1 && getSelectedItem() == null) {
      setSelectedItem(getElementAt(0));
    }
  }

  @Override
  protected final void fireIntervalRemoved(Object source, int index0, int index1) {
    super.fireIntervalRemoved(source, index0, index1);

    if (getSelected() != null && !contains(getSelected())) {
      setSelectedItem(isEmpty() ? null : getElementAt(index0 == 0 ? 0 : index0 - 1));
    }
  }

  @Override
  public void addElement(T item) {
    add(item);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void removeElement(Object obj) {
    T item = ((T) obj);
    remove(item);
  }

  @Override
  public void insertElementAt(T item, int index) {
    add(index, item);
  }

  @Override
  public void removeElementAt(int index) {
    remove(index);
  }
}
