/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author traff
 */
public abstract class AbstractCollectionComboBoxModel<T> extends AbstractListModel implements ComboBoxModel {
  private T mySelection;

  public AbstractCollectionComboBoxModel(@Nullable T selection) {
    mySelection = selection;
  }

  @Override
  public int getSize() {
    return getItems().size();
  }

  @Override
  public T getElementAt(final int index) {
    return getItems().get(index);
  }

  @Override
  public void setSelectedItem(@Nullable Object anItem) {
    //noinspection unchecked
    mySelection = (T)anItem;
    update();
  }

  @Override
  @Nullable
  public Object getSelectedItem() {
    return mySelection;
  }

  @Nullable
  public T getSelected() {
    return mySelection;
  }

  public void update() {
    super.fireContentsChanged(this, -1, -1);
  }

  public boolean contains(T item) {
    return getItems().contains(item);
  }

  @NotNull
  abstract protected List<T> getItems();
}
