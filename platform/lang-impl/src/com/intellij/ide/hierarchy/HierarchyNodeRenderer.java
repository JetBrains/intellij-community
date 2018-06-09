/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class HierarchyNodeRenderer extends NodeRenderer {
  public HierarchyNodeRenderer() {
    setOpaque(false);
    setIconOpaque(false);
    setTransparentIconBackground(true);
  }

  @Override
  protected void doPaint(Graphics2D g) {
    super.doPaint(g);
    setOpaque(false);
  }

  @Override
  public void customizeCellRenderer(final JTree tree, final Object value, final boolean selected, final boolean expanded, final boolean leaf,
                                    final int row, final boolean hasFocus) {
    if (value instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      final Object object = node.getUserObject();
      if (object instanceof HierarchyNodeDescriptor) {
        final HierarchyNodeDescriptor descriptor = (HierarchyNodeDescriptor)object;
        descriptor.getHighlightedText().customize(this);
        setIcon(descriptor.getIcon());
      }
      else if (object instanceof NodeDescriptor) {
        append(((NodeDescriptor)object).getElement().toString());
      }
      else {
        append(object.toString());
      }
    }
  }
}
