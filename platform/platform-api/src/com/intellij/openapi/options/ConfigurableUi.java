// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  default JComponent getPreferredFocusedComponent() {
    return null;
  }
}