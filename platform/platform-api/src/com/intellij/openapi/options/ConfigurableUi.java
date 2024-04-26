// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Implement {@link com.intellij.openapi.Disposable} if you need explicit dispose logic.
 */
public interface ConfigurableUi<S> {
  void reset(@NotNull S settings);

  boolean isModified(@NotNull S settings);

  void apply(@NotNull S settings) throws ConfigurationException;

  @NotNull
  JComponent getComponent();

  default @Nullable JComponent getPreferredFocusedComponent() {
    return null;
  }

  default @Nullable Runnable enableSearch(String option) {
    return null;
  }
}