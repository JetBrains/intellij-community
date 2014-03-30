/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.util.PlatformIcons;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

public abstract class DeleteScopeAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#" + DeleteScopeAction.class.getName());
  private final Tree myTree;

  public DeleteScopeAction(Tree tree) {
    super("Delete Scope", "Delete Scope", PlatformIcons.DELETE_ICON);
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
    InspectionConfigTreeNode parent = null;
    final InspectionConfigTreeNode[] nodes = myTree.getSelectedNodes(InspectionConfigTreeNode.class, null);
    for (InspectionConfigTreeNode node : nodes) {
      final Descriptor descriptor = node.getDescriptor();
      LOG.assertTrue(descriptor != null);
      parent = (InspectionConfigTreeNode)node.getParent();
      final HighlightDisplayKey key = descriptor.getKey();
      if (parent.getChildCount() <= 2) { //remove default with last non-default
        getSelectedProfile().removeAllScopes(key.toString(), e.getProject());
        parent.removeAllChildren();
        parent.setInspectionNode(true);
        parent.setByDefault(true);
      }
      else {
        getSelectedProfile().removeScope(key.toString(), parent.getIndex(node), e.getProject());
        node.removeFromParent();
      }
      ((DefaultTreeModel)myTree.getModel()).reload(parent);
    }
    if (parent != null) {
      myTree.setSelectionPath(new TreePath(parent.getPath()));
    }
    myTree.revalidate();
  }

  protected abstract InspectionProfileImpl getSelectedProfile();
}