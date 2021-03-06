// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class LibraryTreeRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        final NodeDescriptor<?> descriptor = (NodeDescriptor<?>)userObject;
        setIcon(descriptor.getIcon());
        append(descriptor.toString(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, descriptor.getColor()));
      }
    }

    @Override
    public Font getFont() {
      Font font = super.getFont();
      if (font == null) {
        font = StartupUiUtil.getLabelFont();
      }
      return font;
    }
  }
