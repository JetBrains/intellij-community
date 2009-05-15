/*
 * User: anna
 * Date: 14-May-2009
 */
package com.intellij.profile.codeInspection.ui.actions;

import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.profile.codeInspection.ui.InspectionConfigTreeNode;
import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public abstract class MoveScopeAction extends AnAction {
  private final Tree myTree;
  private final int myDir;

  public MoveScopeAction(Tree tree,  String text, Icon icon, int dir) {
    super(text, text, icon);
    myTree = tree;
    myDir = dir;
  }

  protected abstract boolean isEnabledFor(int idx, InspectionConfigTreeNode parent);


  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    if (getSelectedProfile() == null) return;
    final InspectionConfigTreeNode[] nodes = myTree.getSelectedNodes(InspectionConfigTreeNode.class, null);
    if (nodes.length > 0) {
      final InspectionConfigTreeNode treeNode = nodes[0];
      if (treeNode.getScope() != null && !treeNode.isByDefault()) {
        final TreeNode parent = treeNode.getParent();
        final int index = parent.getIndex(treeNode);
        presentation.setEnabled(isEnabledFor(index, (InspectionConfigTreeNode)parent));
      }
    }
  }

  public void actionPerformed(AnActionEvent e) {
    final InspectionConfigTreeNode[] nodes = myTree.getSelectedNodes(InspectionConfigTreeNode.class, null);
    final InspectionConfigTreeNode node = nodes[0];
    final Descriptor descriptor = node.getDesriptor();
    final TreeNode parent = node.getParent();
    final int index = parent.getIndex(node);
    getSelectedProfile().moveScope(descriptor.getKey().toString(), index, myDir);
    node.removeFromParent();
    ((InspectionConfigTreeNode)parent).insert(node, index + myDir);
    ((DefaultTreeModel)myTree.getModel()).reload(parent);
    myTree.setSelectionPath(new TreePath(node.getPath()));
    myTree.revalidate();
  }

  protected abstract InspectionProfileImpl getSelectedProfile();
}