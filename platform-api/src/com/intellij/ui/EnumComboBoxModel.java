/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class EnumComboBoxModel<E extends Enum<E>> extends AbstractListModel implements ComboBoxModel {
  private E mySelected = null;
  private List<E> myList;

  public EnumComboBoxModel(Class<E> en) {
    myList = new ArrayList<E>(EnumSet.allOf(en));
    mySelected = myList.get(0);
  }

  public int getSize() {
    return myList.size();
  }

  public E getElementAt(int index) {
    return myList.get(index);
  }

  public void setSelectedItem(Object item) {
    mySelected = (E)item;
    fireContentsChanged(this, 0, getSize());
  }

  public E getSelectedItem() {
    return mySelected;
  }
}