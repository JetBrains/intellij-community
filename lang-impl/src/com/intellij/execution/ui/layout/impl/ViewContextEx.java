package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.ViewContext;

public interface ViewContextEx extends ViewContext {
  RunnerLayout getLayoutSettings();
}