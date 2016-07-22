/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class EnumComboBoxModel<E extends Enum<E>> extends AbstractListModel implements ComboBoxModel {
  private final List<E> myList;
  private E mySelected = null;

  public EnumComboBoxModel(@NotNull Class<E> en) {
    myList = new ArrayList<>(EnumSet.allOf(en));
    mySelected = myList.get(0);
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
