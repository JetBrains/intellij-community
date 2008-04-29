package com.intellij.execution.ui.actions;

import com.intellij.execution.ui.layout.LayoutViewOptions;

public class FocusOnStartAction extends AbstractFocusOnAction {
  public FocusOnStartAction() {
    super(LayoutViewOptions.STARTUP);
  }
}