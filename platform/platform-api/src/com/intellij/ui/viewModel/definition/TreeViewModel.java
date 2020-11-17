// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

public class TreeViewModel {
  private final ViewModelNode myRoot;

  public TreeViewModel(ViewModelNode root) {
    myRoot = root;
  }

  public ViewModelNode getRoot() {
    return myRoot;
  }
}