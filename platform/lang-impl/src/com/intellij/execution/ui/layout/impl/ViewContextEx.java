package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.ActionGroup;

public interface ViewContextEx extends ViewContext {
  RunnerLayout getLayoutSettings();

  ActionGroup getCellPopupGroup(String place);

  void doWhenInitialized(Runnable runnable);

}