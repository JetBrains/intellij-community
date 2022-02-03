// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class BaseConfigurable implements Configurable {
  protected boolean myModified;

  @Override
  public boolean isModified() {
    return myModified;
  }

  protected void setModified(final boolean modified) {
    myModified = modified;
  }

  // defined here for backward-compatibility
  @SuppressWarnings("RedundantMethodOverride")
  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return null;
  }
}