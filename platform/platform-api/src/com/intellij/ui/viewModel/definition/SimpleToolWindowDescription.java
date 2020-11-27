// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class SimpleToolWindowDescription implements ToolWindowViewModelDescription {

  private final Icon myIcon;
  private final String myId;
  private final String myTitle;
  private final ToolWindowPosition myPosition;

  public SimpleToolWindowDescription(Icon icon, String id, String title, ToolWindowPosition position) {
    myIcon = icon;
    myId = id;
    myTitle = title;
    myPosition = position;
  }

  @Override
  public String getId() {
    return myId;
  }

  @Override
  public String getTitle() { return myTitle; }

  @Override
  public @NotNull Icon getIcon() {
    return myIcon;
  }

  @Override
  public ToolWindowPosition getPosition() {
    return myPosition;
  }
}
