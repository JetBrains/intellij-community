/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.dashboard.hyperlink.RunDashboardHyperlinkComponent;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardTreeMouseListener extends RunDashboardLinkMouseListenerBase {
  @NotNull private final Tree myTree;

  public RunDashboardTreeMouseListener(@NotNull Tree tree) {
    myTree = tree;
  }

  protected void repaintComponent(MouseEvent e) {
    final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final TreeNode treeNode = (TreeNode)path.getLastPathComponent();
      // Invoke nodeChanged() in order to repaint ExpandableItemsHandler's tooltip component.
      ((DefaultTreeModel)myTree.getModel()).nodeChanged(treeNode);
    }

    // Repaint all tree since nodes which cursor just leaved should be repaint too.
    myTree.repaint();
  }

  @Nullable
  @Override
  protected RunDashboardHyperlinkComponent getTagAt(@NotNull MouseEvent e) {
    final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
    if (path == null) return null;

    final Rectangle rectangle = myTree.getPathBounds(path);
    if (rectangle == null) return null;

    int dx = e.getX() - rectangle.x;
    final TreeNode treeNode = (TreeNode)path.getLastPathComponent();
    final int row = myTree.getRowForLocation(e.getX(), e.getY());

    Object tag = null;

    Component component = myTree.getCellRenderer().getTreeCellRendererComponent(myTree, treeNode, false, false, treeNode.isLeaf(), row, true);
    if (component instanceof ColoredTreeCellRenderer) {
      tag = ((ColoredTreeCellRenderer)component).getFragmentTagAt(dx);
    }
    else {
      component.setBounds(rectangle);
      component.doLayout();

      Point componentPoint = new Point(dx, 0);
      Component child = component.getComponentAt(componentPoint);
      if (child instanceof ColoredTreeCellRenderer) {
        Point childPoint = SwingUtilities.convertPoint(component, componentPoint, child);
        tag = ((ColoredTreeCellRenderer)child).getFragmentTagAt(childPoint.x);
      }
      else if (child instanceof JLabel) {
        tag = ((JLabel)child).getIcon();
      }
    }

    if (tag instanceof RunDashboardHyperlinkComponent) {
      return (RunDashboardHyperlinkComponent)tag;
    }
    return null;
  }
}

