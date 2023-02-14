// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ComponentContainer extends Disposable {
  @NotNull
  JComponent getComponent();

  JComponent getPreferredFocusableComponent();
}
