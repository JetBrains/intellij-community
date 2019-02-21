// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.dashboard.hyperlink.RunDashboardHyperlinkComponent;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
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

  @Override
  protected Object getAimedObject(MouseEvent e) {
    Object aimedObject = null;
    final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      DefaultMutableTreeNode treeNode = ObjectUtils.tryCast(path.getLastPathComponent(), DefaultMutableTreeNode.class);
      if (treeNode != null) {
        aimedObject = treeNode.getUserObject();
      }
    }
    return aimedObject;
  }

  @Override
  protected void repaintComponent(MouseEvent e) {
    TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
    Rectangle bounds = path == null ? null : myTree.getPathBounds(path);
    if (bounds != null) {
      myTree.repaint(bounds);
    }
  }

  @Nullable
  @Override
  protected RunDashboardHyperlinkComponent getTagAt(@NotNull MouseEvent e) {
    final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
    if (path == null) return null;

    final Rectangle rectangle = myTree.getPathBounds(path);
    if (rectangle == null) return null;

    int dx = e.getX() - rectangle.x;
    final Object treeNode = path.getLastPathComponent();
    final int row = myTree.getRowForLocation(e.getX(), e.getY());
    boolean isLeaf = myTree.getModel().isLeaf(treeNode);

    Object tag = null;

    Component component = myTree.getCellRenderer().getTreeCellRendererComponent(myTree, treeNode, true, false, isLeaf, row, true);
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

