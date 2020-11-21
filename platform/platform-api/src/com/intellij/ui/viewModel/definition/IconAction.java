// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

import javax.swing.*;

public class IconAction implements ActionViewModel {

  private final Icon myIcon;
  private final String myTooltipText;
  private final Runnable myExecuteAction;

  public IconAction(Icon icon, String tooltipText, Runnable action) {
    myIcon = icon;
    myTooltipText = tooltipText;
    myExecuteAction = action;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getTooltipText() {
    return myTooltipText;
  }

  public Runnable getExecuteAction() {
    return myExecuteAction;
  }
}
