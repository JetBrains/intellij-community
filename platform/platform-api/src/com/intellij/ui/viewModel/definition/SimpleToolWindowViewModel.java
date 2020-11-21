// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SimpleToolWindowViewModel implements ToolWindowViewModel {

  private final ActionBarViewModel myActionBarViewModel;
  private final TreeViewModel myTreeModel;
  private final Icon myIcon;
  private String myId;

  public SimpleToolWindowViewModel(ActionBarViewModel bar, TreeViewModel model, Icon icon, String id) {
    myActionBarViewModel = bar;
    myTreeModel = model;
    myIcon = icon;
    myId = id;
  }

  @Override
  public String getId() {
    return myId;
  }

  @Override
  public @NotNull ActionBarViewModel getActions() {
    return myActionBarViewModel;
  }

  @Override
  public @NotNull TreeViewModel getTree() {
    return myTreeModel;
  }

  @Override
  public @NotNull Icon getIcon() {
    return myIcon;
  }
}
