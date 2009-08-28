package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class CustomizeColoredTreeCellRenderer {
  public abstract void customizeCellRenderer(final SimpleColoredComponent renderer, final JTree tree, final Object value, final boolean selected,
    final boolean expanded, final boolean leaf, final int row, final boolean hasFocus);

  @Nullable
  public Object getTag() {
    return null;
  }
}
