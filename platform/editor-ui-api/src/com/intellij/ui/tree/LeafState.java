// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import org.jetbrains.annotations.NotNull;

public enum LeafState {
  /**
   * Specifies that a tree should not show the collapse/expand icon for the node, because this node cannot have any children.
   *
   * @see AbstractTreeNode#isAlwaysLeaf()
   */
  ALWAYS,
  /**
   * Specifies that a tree should always show the collapse/expand icon for the node, even if this node have no children.
   *
   * @see AbstractTreeNode#isAlwaysShowPlus()
   */
  NEVER,
  /**
   * Specifies that a tree should show the collapse/expand icon for the node, only if this node have some children.
   * By default, children are counted immediately that may cause performance problems.
   *
   * @see javax.swing.tree.TreeModel#getChildCount(Object)
   */
  DEFAULT,
  /**
   * Specifies that a tree should show the collapse/expand icon for the node, only if this node have some children.
   * In this mode children are counted later that may cause a "blinking" of the collapse/expand icon.
   */
  ASYNC;

  public interface Supplier {
    /**
     * @return a leaf state for the tree node, that implements this interface
     * @see javax.swing.tree.TreeModel#isLeaf(Object)
     */
    @NotNull
    LeafState getLeafState();
  }
}
