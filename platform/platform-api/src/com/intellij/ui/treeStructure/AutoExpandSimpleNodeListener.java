// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure;

import com.intellij.util.ui.tree.TreeModelAdapter;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.TreePath;

public class AutoExpandSimpleNodeListener extends TreeModelAdapter {
  private final JTree myTree;

  public AutoExpandSimpleNodeListener(@NotNull JTree tree) {
    myTree = tree;
  }

  @Override
  public void treeNodesInserted(TreeModelEvent event) {
    TreePath path = event.getTreePath();
    if (path == null) return;

    if (!myTree.isVisible(path) || !myTree.isExpanded(path)) return;

    for (Object child : event.getChildren()) {
      SimpleNode node = TreeUtil.getUserObject(SimpleNode.class, child);
      if (node != null && node.isAutoExpandNode()) {
        TreeUtil.promiseExpand(myTree, event.getTreePath().pathByAddingChild(child));
      }
    }
  }
}
