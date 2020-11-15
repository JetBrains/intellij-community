// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel;

import javax.swing.*;

public class IconAction extends ActionBarItem {

  private final Icon myIcon;
  private final String myText;
  private final Runnable myExecuteAction;

  public IconAction(Icon icon, String text, Runnable action) {
    myIcon = icon;
    myText = text;
    myExecuteAction = action;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public String getTooltipText() {
    return myText;
  }

  public Runnable executeAction() {
    return myExecuteAction;
  }
}
