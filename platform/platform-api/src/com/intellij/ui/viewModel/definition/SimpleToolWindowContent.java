// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

import org.jetbrains.annotations.NotNull;

public class SimpleToolWindowContent implements ToolWindowViewModelContent {

  private final ActionBarViewModel myActionBarViewModel;
  private final TreeViewModel myTreeModel;

  public SimpleToolWindowContent(ActionBarViewModel model, TreeViewModel treeModel) {
    myActionBarViewModel = model;
    myTreeModel = treeModel;
  }

  @Override
  public @NotNull ActionBarViewModel getActions() {
    return myActionBarViewModel;
  }

  @Override
  public @NotNull TreeViewModel getTree() {
    return myTreeModel;
  }
}
