// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ListItemDescriptorAdapter<T> implements ListItemDescriptor<T> {
  @Override
  public @Nullable String getCaptionAboveOf(T value) {
    return null;
  }

  @Override
  public @Nullable String getTooltipFor(T value) {
    return null;
  }

  @Override
  public Icon getIconFor(T value) {
    return null;
  }

  @Override
  public boolean hasSeparatorAboveOf(T value) {
    return false;
  }
}