/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.ui.SortedListModel;

import javax.swing.*;
import java.util.Comparator;

public class SortedComboBoxModel<T> extends SortedListModel<T> implements ComboBoxModel {
  private T mySelection;

  public SortedComboBoxModel(Comparator<T> comparator) {
    super(comparator);
  }

  public T getSelectedItem() {
    return mySelection;
  }

  public void setSelectedItem(Object anItem) {
    if (Comparing.equal(mySelection, anItem)) return;
    mySelection = (T)anItem;
    fireSelectionChanged();
  }

  private void fireSelectionChanged() {
    fireContentsChanged(this, -1, -1);
  }
}
