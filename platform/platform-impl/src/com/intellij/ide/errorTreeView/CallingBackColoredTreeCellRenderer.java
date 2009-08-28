package com.intellij.ide.errorTreeView;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;

import javax.swing.*;

public class CallingBackColoredTreeCellRenderer extends ColoredTreeCellRenderer {
  private CustomizeColoredTreeCellRenderer myCurrentCallback;

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (myCurrentCallback != null) {
      myCurrentCallback.customizeCellRenderer(this, tree, value, selected, expanded, leaf, row, hasFocus);
    }
  }

  public void setCurrentCallback(final CustomizeColoredTreeCellRenderer currentCallback) {
    myCurrentCallback = currentCallback;
  }
}
