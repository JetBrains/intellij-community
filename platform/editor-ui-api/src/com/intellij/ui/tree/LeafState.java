// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeModel;

public enum LeafState {
  /**
   * Specifies that a tree should not show the collapse/expand icon for the node, because this node cannot have any children.
   *
   * @see AbstractTreeNode#isAlwaysLeaf()
   */
  ALWAYS,
  /**
   * Specifies that a tree should always show the collapse/expand icon for the node, even if this node has no children.
   *
   * @see AbstractTreeNode#isAlwaysShowPlus()
   */
  NEVER,
  /**
   * Specifies that a tree should show the collapse/expand icon for the node only if this node has some children.
   * By default, children are counted immediately that may cause performance problems.
   *
   * @see TreeModel#getChildCount(Object)
   */
  DEFAULT,
  /**
   * Specifies that a tree should show the collapse/expand icon for the node only if this node has some children.
   * In this mode children are counted later that may cause a "blinking" of the collapse/expand icon.
   */
  ASYNC;

  public interface Supplier {
    /**
     * @return a leaf state for the tree node, that implements this interface
     * @see TreeModel#isLeaf(Object)
     */
    @NotNull
    LeafState getLeafState();
  }

  /**
   * @param node a tree node, which leaf state interested in
   * @return a leaf state of the specified tree node, or {@link #DEFAULT} value
   */
  @ApiStatus.Internal
  public static @NotNull LeafState get(@NotNull Object node) {
    return node instanceof Supplier ? ((Supplier)node).getLeafState() : DEFAULT;
  }

  /**
   * @param node  a tree node, which leaf state interested in
   * @param model a tree model used to resolve a leaf state of the specified tree node
   * @return a resolved leaf state of the specified tree node according to the given model
   */
  @ApiStatus.Internal
  public static @NotNull LeafState get(@NotNull Object node, @NotNull TreeModel model) {
    LeafState state = get(node);
    return state != DEFAULT ? state : model.isLeaf(node) ? ALWAYS : NEVER;
  }
}
