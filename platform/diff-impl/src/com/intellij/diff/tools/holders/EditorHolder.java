// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.holders;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.FocusListener;

public abstract class EditorHolder implements Disposable {
  public abstract @NotNull JComponent getComponent();

  public abstract @Nullable JComponent getPreferredFocusedComponent();

  public void installFocusListener(@NotNull FocusListener listener) {
  }
}
