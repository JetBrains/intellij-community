// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.ui.actions;

import com.intellij.execution.ui.layout.LayoutViewOptions;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class FocusOnStartAction extends AbstractFocusOnAction {
  public FocusOnStartAction() {
    super(LayoutViewOptions.STARTUP);
  }
}