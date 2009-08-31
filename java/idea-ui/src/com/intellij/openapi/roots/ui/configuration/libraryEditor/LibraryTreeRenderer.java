package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class LibraryTreeRenderer extends ColoredTreeCellRenderer {
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        final NodeDescriptor descriptor = (NodeDescriptor)userObject;
        final Icon closedIcon = descriptor.getClosedIcon();
        Icon openIcon = descriptor.getOpenIcon();
        if (openIcon == null) {
          openIcon = closedIcon;
        }
        setIcon(expanded? openIcon : closedIcon);
        append(descriptor.toString(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, descriptor.getColor()));
      }
    }

    public Font getFont() {
      Font font = super.getFont();
      if (font == null) {
        font = UIUtil.getLabelFont();
      }
      return font;
    }
  }
