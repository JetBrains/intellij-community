package com.intellij.openapi.vcs.changes.issueLinks;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public class TreeNodePartListener extends LinkMouseListenerBase {
  private final ClickableTreeCellRenderer myRenderer;
  //recalc optimization
  private DefaultMutableTreeNode myLastHitNode;
  private Component myRenderedComp;

  public TreeNodePartListener(ClickableTreeCellRenderer renderer) {
    myRenderer = renderer;
  }

  protected Object getTagAt(final MouseEvent e) {
    final JTree tree = (JTree) e.getSource();
    final TreePath path = tree.getPathForLocation(e.getX(), e.getY());
    if (path != null) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) path.getLastPathComponent();
      if (myLastHitNode != treeNode) {
        myLastHitNode = treeNode;
        myRenderedComp = myRenderer.getTreeCellRendererComponent(tree, treeNode, false, false, treeNode.isLeaf(), -1, false);
      }

      if (myRenderedComp != null) {
        final int compX = myRenderedComp.getX();
        final int compY = myRenderedComp.getY();
        if ((compX < e.getX()) && ((compX + myRenderedComp.getWidth()) > e.getX()) &&
          (compY < e.getY()) && ((compY + myRenderedComp.getHeight()) > e.getY())) {
          return myRenderer.getTag();
        }
      }
    }
    return null;
  }
}
