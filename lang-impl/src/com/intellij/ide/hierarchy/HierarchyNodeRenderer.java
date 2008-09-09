package com.intellij.ide.hierarchy;

import com.intellij.ui.ColoredTreeCellRenderer;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public final class HierarchyNodeRenderer extends ColoredTreeCellRenderer {
  public void customizeCellRenderer(final JTree tree, final Object value, final boolean selected, final boolean expanded, final boolean leaf,
                                    final int row, final boolean hasFocus) {
    if (value instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      final Object object = node.getUserObject();
      if (object instanceof HierarchyNodeDescriptor) {
        final HierarchyNodeDescriptor descriptor = (HierarchyNodeDescriptor)object;
        descriptor.getHighlightedText().customize(this);
        if (expanded){
          setIcon(descriptor.getOpenIcon());
        }
        else{
          setIcon(descriptor.getClosedIcon());
        }
      }
    }
  }
}
