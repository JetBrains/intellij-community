/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.todo;

import com.intellij.ide.todo.nodes.SummaryNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ui.HighlightableCellRenderer;
import com.intellij.ui.HighlightedRegion;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * todo: replace this highlightable crap with regular NodeRenderer
 * @author Vladimir Kondratyev
 */
final class TodoCompositeRenderer implements TreeCellRenderer {
  private final NodeRenderer myNodeRenderer;
  private final HighlightableCellRenderer myColorTreeCellRenderer;

  public TodoCompositeRenderer() {
    myNodeRenderer = new NodeRenderer();
    myColorTreeCellRenderer = new HighlightableCellRenderer();
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree, Object obj, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    Component result;

    Object userObject = ((DefaultMutableTreeNode)obj).getUserObject();
    if (userObject instanceof SummaryNode) {
      myNodeRenderer.getTreeCellRendererComponent(tree, userObject.toString(), selected, expanded, leaf, row, hasFocus);
      myNodeRenderer.setFont(UIUtil.getTreeFont().deriveFont(Font.BOLD));
      myNodeRenderer.setIcon(null);
      result = myNodeRenderer;
    }
    else if (userObject instanceof NodeDescriptor && userObject instanceof HighlightedRegionProvider) {
      NodeDescriptor descriptor = (NodeDescriptor)userObject;
      HighlightedRegionProvider regionProvider = (HighlightedRegionProvider)userObject;
      myColorTreeCellRenderer.getTreeCellRendererComponent(tree, obj, selected, expanded, leaf, row, hasFocus);
      for (HighlightedRegion region : regionProvider.getHighlightedRegions()) {
        myColorTreeCellRenderer.addHighlighter(region.startOffset, region.endOffset, region.textAttributes);
      }
      myColorTreeCellRenderer.setIcon(descriptor.getIcon());
      result = myColorTreeCellRenderer;
    }
    else {
      result = myNodeRenderer.getTreeCellRendererComponent(tree, null, selected, expanded, leaf, row, hasFocus);
    }

    if (result instanceof JComponent) {
      ((JComponent)result).setOpaque(!selected);
    }

    return result;
  }
}
