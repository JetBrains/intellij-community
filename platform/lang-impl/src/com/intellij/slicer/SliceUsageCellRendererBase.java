// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public abstract class SliceUsageCellRendererBase extends ColoredTreeCellRenderer {
  public SliceUsageCellRendererBase() {
    setOpaque(false);
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    assert value instanceof DefaultMutableTreeNode;
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
    Object userObject = treeNode.getUserObject();
    if (userObject == null) return;
    if (userObject instanceof MyColoredTreeCellRenderer) {
      MyColoredTreeCellRenderer node = (MyColoredTreeCellRenderer)userObject;
      node.customizeCellRenderer(this, tree, value, selected, expanded, leaf, row, hasFocus);
      if (node instanceof SliceNode) {
        setToolTipText(((SliceNode)node).getPresentation().getTooltip());
      }
    }
    else {
      @NlsSafe String userObjectString = userObject.toString();
      append(userObjectString, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  public abstract void customizeCellRendererFor(@NotNull SliceUsage sliceUsage);
}

