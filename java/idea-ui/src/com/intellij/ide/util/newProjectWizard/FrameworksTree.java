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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author nik
 */
public class FrameworksTree extends CheckboxTree {
  private boolean myProcessingMouseEventOnCheckbox;

  public FrameworksTree(List<List<FrameworkSupportNode>> groups) {
    super(new FrameworksTreeRenderer(), new FrameworksRootNode(groups), new CheckPolicy(false, true, true, false));
    setRootVisible(false);
    setShowsRootHandles(false);
    TreeUtil.expandAll(this);
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    final TreePath path = getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final Object node = path.getLastPathComponent();
      if (node instanceof FrameworkSupportNode) {
        final Rectangle checkboxBounds = ((CheckboxTreeCellRendererBase)getCellRenderer()).myCheckbox.getBounds();
        final Rectangle pathBounds = getPathBounds(path);
        checkboxBounds.setLocation(pathBounds.getLocation());
        if (checkboxBounds.contains(e.getPoint())) {
          try {
            myProcessingMouseEventOnCheckbox = true;
            super.processMouseEvent(e);
          }
          finally {
            myProcessingMouseEventOnCheckbox = false;
          }
          return;
        }
      }
    }

    super.processMouseEvent(e);
  }

  @Override
  protected void installSpeedSearch() {
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      @Override
      public String convert(TreePath path) {
        final Object node = path.getLastPathComponent();
        if (node instanceof FrameworkSupportNode) {
          return ((FrameworkSupportNode)node).getTitle();
        }
        return "";
      }
    });
  }

  public boolean isProcessingMouseEventOnCheckbox() {
    return myProcessingMouseEventOnCheckbox;
  }

  private static class FrameworksTreeRenderer extends CheckboxTreeCellRenderer {
    private FrameworksTreeRenderer() {
      super(true, false);
    }

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof FrameworkSupportNode) {
        final FrameworkSupportNode node = (FrameworkSupportNode)value;
        getTextRenderer().append(node.getTitle());
        getTextRenderer().setIcon(node.getProvider().getIcon());
      }
    }
  }

  private static class FrameworksRootNode extends CheckedTreeNode {
    public FrameworksRootNode(List<List<FrameworkSupportNode>> groups) {
      super(null);
      for (List<FrameworkSupportNode> group : groups) {
        for (FrameworkSupportNode node : group) {
          add(node);
        }
      }
    }
  }
}
