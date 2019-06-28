// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ListItemDescriptorAdapter<T> implements ListItemDescriptor<T> {
  @Nullable
  @Override
  public String getCaptionAboveOf(T value) {
    return null;
  }

  @Nullable
  @Override
  public String getTooltipFor(T value) {
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