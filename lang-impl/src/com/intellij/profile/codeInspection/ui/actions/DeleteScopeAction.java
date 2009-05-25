/*
 * User: anna
 * Date: 14-May-2009
 */
package com.intellij.profile.codeInspection.ui.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.profile.codeInspection.ui.InspectionConfigTreeNode;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Icons;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public abstract class DeleteScopeAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + DeleteScopeAction.class.getName());
  private final Tree myTree;

  public DeleteScopeAction(Tree tree) {
    super("Delete Scope", "Delete Scope", Icons.DELETE_ICON);
    myTree = tree;
    registerCustomShortcutSet(CommonShortcuts.DELETE, myTree);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    if (getSelectedProfile() == null) return;
    final InspectionConfigTreeNode[] nodes = myTree.getSelectedNodes(InspectionConfigTreeNode.class, null);
    if (nodes.length > 0) {
      for (InspectionConfigTreeNode node : nodes) {
        if (node.getScopeName() == null || node.isByDefault()) return;
      }
      presentation.setEnabled(true);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final InspectionConfigTreeNode[] nodes = myTree.getSelectedNodes(InspectionConfigTreeNode.class, null);
    for (InspectionConfigTreeNode node : nodes) {
      final Descriptor descriptor = node.getDesriptor();
      LOG.assertTrue(descriptor != null);
      final InspectionConfigTreeNode parent = (InspectionConfigTreeNode)node.getParent();
      final HighlightDisplayKey key = descriptor.getKey();
      if (parent.getChildCount() <= 2) { //remove default with last non-default
        getSelectedProfile().removeAllScopes(key.toString());
        parent.removeAllChildren();
        parent.setInspectionNode(true);
        parent.setByDefault(true);
      } else {
        getSelectedProfile().removeScope(key.toString(), parent.getIndex(node));
        node.removeFromParent();
      }
      ((DefaultTreeModel)myTree.getModel()).reload(parent);
      myTree.setSelectionPath(new TreePath(parent.getPath()));
      myTree.revalidate();
    }
  }

  protected abstract InspectionProfileImpl getSelectedProfile();
}