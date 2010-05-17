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

import javax.swing.*;
import java.util.List;

/**
 * @author yole
*/
public class CollectionComboBoxModel extends AbstractListModel implements ComboBoxModel {
  private final List myItems;
  private Object mySelection;

  public CollectionComboBoxModel(final List items, final Object selection) {
    myItems = items;
    mySelection = selection;
  }

  public int getSize() {
    return myItems.size();
  }

  public Object getElementAt(final int index) {
    return myItems.get(index);
  }

  public void setSelectedItem(final Object anItem) {
    mySelection = anItem;
  }

  public Object getSelectedItem() {
    return mySelection;
  }

  public void update() {
    super.fireContentsChanged(this, -1, -1);
  }
}
