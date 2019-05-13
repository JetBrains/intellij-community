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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author traff
 */
public class CollectionComboBoxModel<T> extends CollectionListModel<T> implements ComboBoxModel<T> {
  protected T mySelection;

  public CollectionComboBoxModel() {
    super();
    mySelection = null;
  }

  public CollectionComboBoxModel(@NotNull List<T> items) {
    this(items, ContainerUtil.getFirstItem(items));
  }

  public CollectionComboBoxModel(@NotNull List<T> items, @Nullable T selection) {
    super(items, true);
    mySelection = selection;
  }

  @Override
  public void setSelectedItem(@Nullable Object item) {
    if (mySelection != item) {
      @SuppressWarnings("unchecked") T t = (T)item;
      mySelection = t;
      update();
    }
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

}