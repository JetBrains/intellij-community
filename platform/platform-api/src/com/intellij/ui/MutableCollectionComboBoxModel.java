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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class MutableCollectionComboBoxModel<T> extends AbstractCollectionComboBoxModel<T> {
  public MutableCollectionComboBoxModel(@NotNull List<T> items) {
    this(items, ContainerUtil.getFirstItem(items));
  }

  public MutableCollectionComboBoxModel() {
    super(null);
  }

  public MutableCollectionComboBoxModel(@NotNull List<T> items, @Nullable T selection) {
    super(selection, items);
  }

  public void update(@NotNull List<T> items) {
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
}
