// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ui.components.JBList;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

class SEListSelectionTracker implements ListSelectionListener {

  private final JBList<?> myList;
  private final SearchEverywhereUI.SearchListModel myListModel;

  private boolean locked;
  private final List<Object> selectedItems = new ArrayList<>();

  SEListSelectionTracker(JBList<?> list, SearchEverywhereUI.SearchListModel model) {
    myList = list;
    myListModel = model;
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    if (locked) return;

    saveSelection();
  }

  void saveSelection() {
    selectedItems.clear();
    selectedItems.addAll(myList.getSelectedValuesList());
  }

  void restoreSelection() {
    locked = true;
    try {
      int[] indicesToSelect = calcIndicesToSelect();

      if (indicesToSelect.length > 0) {
        myList.setSelectedIndices(indicesToSelect);
      }
      else {
        myList.setSelectedIndex(0);
      }
    }
    finally {
      locked = false;
    }
  }

  void resetSelectionIfNeeded() {
    int[] indices = calcIndicesToSelect();
    if (indices.length == 0) {
      selectedItems.clear();
    }
  }

  void setLocked(boolean lock) {
    locked = lock;
  }

  private int[] calcIndicesToSelect() {
    List<Object> items = myListModel.getItems();
    if (items.isEmpty()) return new int[0];

    return IntStream.range(0, items.size())
      .filter(i -> selectedItems.contains(items.get(i)))
      .toArray();
  }

}
