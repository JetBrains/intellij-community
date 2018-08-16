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
package com.intellij.ide;

import com.intellij.pom.Navigatable;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Enumeration;

public abstract class OccurenceNavigatorSupport implements OccurenceNavigator {
  private final JTree myTree;

  public OccurenceNavigatorSupport(@NotNull JTree tree) {
    myTree = tree;
  }

  @Nullable
  protected abstract Navigatable createDescriptorForNode(@NotNull DefaultMutableTreeNode node);

  /**
   * @return true if this node is an actual occurrence, i.e. the "next/prev occurrence" actions should show this node (as opposed to groups or other nodes which should be skipped)
   * Override in your occurrence support for more efficient impl
   */
  protected boolean isOccurrenceNode(@NotNull DefaultMutableTreeNode node) {
    return createDescriptorForNode(node) != null;
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    DefaultMutableTreeNode node = findNode(myTree, true);
    if (node == null) return null;
    TreePath treePath = new TreePath(node.getPath());
    TreeUtil.selectPath(myTree, treePath);
    Navigatable editSourceDescriptor = createDescriptorForNode(node);
    if (editSourceDescriptor == null) return null;
    Counters counters = calculatePosition(node);
    return new OccurenceInfo(editSourceDescriptor, counters.myFoundOccurenceNumber, counters.myOccurencesCount);
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    DefaultMutableTreeNode node = findNode(myTree, false);
    if (node == null) return null;
    TreePath treePath = new TreePath(node.getPath());
    TreeUtil.selectPath(myTree, treePath);
    Navigatable editSourceDescriptor = createDescriptorForNode(node);
    if (editSourceDescriptor == null) return null;
    Counters counters = calculatePosition(node);
    return new OccurenceInfo(editSourceDescriptor, counters.myFoundOccurenceNumber, counters.myOccurencesCount);
  }

  @NotNull
  private Counters calculatePosition(@NotNull DefaultMutableTreeNode foundNode) {
    Counters counters = new Counters();
    @SuppressWarnings("unchecked")
    Enumeration<TreeNode> enumeration = ((DefaultMutableTreeNode)foundNode.getRoot()).preorderEnumeration();
    while (enumeration.hasMoreElements()) {
      TreeNode node = enumeration.nextElement();
      if (node instanceof DefaultMutableTreeNode && isOccurrenceNode((DefaultMutableTreeNode)node)) {
        counters.myOccurencesCount++;
      }
      if (node == foundNode) {
        counters.myFoundOccurenceNumber = counters.myOccurencesCount;
      }
    }
    return counters;
  }

  @Override
  public boolean hasNextOccurence() {
    DefaultMutableTreeNode node = findNode(myTree, true);
    return node != null;
  }

  @Override
  public boolean hasPreviousOccurence() {
    DefaultMutableTreeNode node = findNode(myTree, false);
    return node != null;
  }

  private static class Counters {
    /**
     * Equals to {@code -1} if this value is unsupported.
     */
    int myFoundOccurenceNumber; // starts with 1
    /**
     * Equals to {@code -1} if this value is unsupported.
     */
    int myOccurencesCount;
  }

  private DefaultMutableTreeNode findNode(@NotNull JTree tree, boolean forward) {
    TreePath selectionPath = tree.getSelectionPath();
    TreeNode selectedNode = null;
    if (selectionPath != null) {
      selectedNode = (TreeNode)selectionPath.getLastPathComponent();
    }
    return findNextNodeAfter(tree, selectedNode, forward);
  }

  public DefaultMutableTreeNode findNextNodeAfter(@NotNull JTree tree, TreeNode selectedNode, boolean forward) {
    if (selectedNode == null) {
      selectedNode = (TreeNode)tree.getModel().getRoot();
    }
    if (forward) {
      for (DefaultMutableTreeNode node=((DefaultMutableTreeNode)selectedNode).getNextNode(); node != null; node = node.getNextNode()) {
        if (createDescriptorForNode(node) != null) {
          return node;
        }
      }
    }
    else {
      for (DefaultMutableTreeNode node=((DefaultMutableTreeNode)selectedNode).getPreviousNode(); node != null; node = node.getPreviousNode()) {
        if (createDescriptorForNode(node) != null) {
          return node;
        }
      }
    }

    return null;
  }
}
