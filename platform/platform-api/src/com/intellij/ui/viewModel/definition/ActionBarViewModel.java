// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

import com.intellij.util.containers.SortedList;

import java.util.List;

public class ActionBarViewModel {

  private final List<ActionViewModel> myItems;

  public ActionBarViewModel(List<ActionViewModel> items) {
    myItems = items;
  }

  public List<ActionViewModel> getActionButtons() {
    return myItems;
  }
}
