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

import com.intellij.openapi.util.Pair;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 * This component represents a list of checkboxes.
 */
public abstract class OptionalChooserComponent<T> implements CheckBoxListListener, ComponentWithEmptyText {
  private JPanel myContentPane;
  private CheckBoxList myList;
  private DefaultListModel myListModel;
  private List<Pair<T, Boolean>> myInitialList;
  private ArrayList<Pair<T, Boolean>> myWorkingList;

  public OptionalChooserComponent(@NotNull final List<Pair<T, Boolean>> list) {
    setInitialList(list);
    myWorkingList = new ArrayList<>(myInitialList);

    // fill list
    reset();
  }

  @NotNull
  @Override
  public StatusText getEmptyText() {
    return myList.getEmptyText();
  }

  public JPanel getContentPane() {
    return myContentPane;
  }

  public void checkBoxSelectionChanged(int index, boolean value) {
    final Pair<T, Boolean> pair = myWorkingList.remove(index);
    myWorkingList.add(index, Pair.create(pair.first, value));
  }

  private void createUIComponents() {
    myList = new CheckBoxList(this);
    myList.setBorder(null);
    myListModel = (DefaultListModel)myList.getModel();
  }

  public void reset() {
    myWorkingList = new ArrayList<>(myInitialList);
    refresh();
  }

  protected abstract JCheckBox createCheckBox(final T value, final boolean checked);

  public int getSelectedIndex() {
    return myList.getSelectedIndex();
  }

  public void setSelectedIndex(final int index) {
    myList.setSelectedIndex(index);
  }

  public boolean removeAt(final int index) {
    getCurrentModel().remove(index);
    refresh();

    if (index < getCurrentModel().size()) {
      setSelectedIndex(index);
      return true;
    }
    else if (index > 0) {
      setSelectedIndex(index - 1);
      return true;
    }
    return false;
  }

  public boolean removeSelected() {
    final int selectedIndex = getSelectedIndex();
    // selected index
    if (selectedIndex != -1) {
      return removeAt(selectedIndex);
    }
    return false;
  }

  public boolean isModified() {
    return !myWorkingList.equals(myInitialList);
  }

  public void setInitialList(@NotNull final List<Pair<T, Boolean>> list) {
    myInitialList = list;
  }

  public ArrayList<Pair<T, Boolean>> getCurrentModel() {
    return myWorkingList;
  }

  public void apply() {
    myInitialList.clear();
    myInitialList.addAll(myWorkingList);
  }

  public void refresh() {
    myListModel.clear();
    for (Pair<T, Boolean> pair : myWorkingList) {
      myListModel.addElement(createCheckBox(pair.first, pair.second));
    }
  }

  public CheckBoxList getList() {
    return myList;
  }
}
