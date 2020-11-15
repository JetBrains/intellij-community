// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel;

import com.intellij.util.containers.SortedList;

public class ActionBar {

  private final SortedList<ActionBarItem> myItems;

  public ActionBar(SortedList<ActionBarItem> items) {
    myItems = items;
  }

  public SortedList<ActionBarItem> getActionButtons() {
    return myItems;
  }
}
