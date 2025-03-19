// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class EnumComboBoxModel<E extends Enum<E>> extends AbstractListModel<E> implements ComboBoxModel<E> {
  private final List<E> myList;
  private E mySelected;

  public EnumComboBoxModel(@NotNull Class<E> en) {
    myList = new ArrayList<>(createEnumSet(en));
    mySelected = myList.get(0);
  }

  protected @NotNull EnumSet<E> createEnumSet(@NotNull Class<E> en) {
    return EnumSet.allOf(en);
  }

  @Override
  public int getSize() {
    return myList.size();
  }

  @Override
  public E getElementAt(int index) {
    return myList.get(index);
  }

  @Override
  public void setSelectedItem(Object item) {
    @SuppressWarnings("unchecked") E e = (E)item;
    setSelectedItem(e);
  }

  public void setSelectedItem(E item) {
    mySelected = item;
    fireContentsChanged(this, 0, getSize());
  }

  @Override
  public E getSelectedItem() {
    return mySelected;
  }
}
