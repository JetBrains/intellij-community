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
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
*/
public class CollectionListModel extends AbstractListModel {
  private final List myItems;

  public CollectionListModel(final List items) {
    myItems = items;
  }

  public CollectionListModel(final Object... items) {
    myItems = Arrays.asList(items);
  }

  public int getSize() {
    return myItems.size();
  }

  public Object getElementAt(int index) {
    return myItems.get(index);
  }

  public void add(Object element) {
    int i = myItems.size();
    myItems.add(element);
    fireIntervalAdded(this, i, i);
  }

  public void remove(Object element) {
    int i = myItems.indexOf(element);
    myItems.remove(element);
    fireIntervalRemoved(this, i, i);
  }

  public void remove(int index) {
    myItems.remove(index);
    fireIntervalRemoved(this, index, index);
  }

  public void removeAll() {
    int size = myItems.size();
    myItems.clear();
    fireIntervalRemoved(this, 0, size - 1);
  }

  public void contentsChanged(Object element) {
    int i = myItems.indexOf(element);
    fireContentsChanged(this, i, i);
  }
}
