// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.options.UnnamedConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class LibraryPropertiesEditor implements UnnamedConfigurable {
  @Override
  public abstract @NotNull JComponent createComponent();

  @Override
  public abstract void apply();
}
