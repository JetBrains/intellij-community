// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class CustomizeColoredTreeCellRenderer {
  public abstract void customizeCellRenderer(final SimpleColoredComponent renderer, final JTree tree, final Object value, final boolean selected,
    final boolean expanded, final boolean leaf, final int row, final boolean hasFocus);

  public @Nullable Object getTag() {
    return null;
  }
}
