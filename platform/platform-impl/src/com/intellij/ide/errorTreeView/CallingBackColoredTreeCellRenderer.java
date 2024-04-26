// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.errorTreeView;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class CallingBackColoredTreeCellRenderer extends ColoredTreeCellRenderer {
  private CustomizeColoredTreeCellRenderer myCurrentCallback;

  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (myCurrentCallback != null) {
      myCurrentCallback.customizeCellRenderer(this, tree, value, selected, expanded, leaf, row, hasFocus);
    }
  }

  public void setCurrentCallback(final CustomizeColoredTreeCellRenderer currentCallback) {
    myCurrentCallback = currentCallback;
  }
}
