/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 19, 2005
 */
abstract class RuleAction extends ToggleAction {
  private UsageViewImpl myView;
  private boolean myState;

  public RuleAction(UsageViewImpl view, final String text, final Icon icon) {
    super(text, null, icon);
    myView = view;
    myState = getOptionValue();
  }

  protected abstract boolean getOptionValue();

  protected abstract void setOptionValue(boolean value);

  public boolean isSelected(AnActionEvent e) {
    return myState;
  }

  public void setSelected(AnActionEvent e, boolean state) {
    setOptionValue(state);
    myState = state;
    myView.rulesChanged();
  }
}
