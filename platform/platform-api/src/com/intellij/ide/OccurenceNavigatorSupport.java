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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public abstract class OccurenceNavigatorSupport implements OccurenceNavigator {
  private final JTree myTree;

  public OccurenceNavigatorSupport(@NotNull JTree tree) {
    myTree = tree;
  }

  @Nullable
  protected abstract Navigatable createDescriptorForNode(DefaultMutableTreeNode node);

  @Override
  public OccurenceInfo goNextOccurence() {
    Counters counters = new Counters();
    DefaultMutableTreeNode node = findNode(myTree, true, counters);
    if (node == null) return null;
    TreePath treePath = new TreePath(node.getPath());
    TreeUtil.selectPath(myTree, treePath);
    Navigatable editSourceDescriptor = createDescriptorForNode(node);
    if (editSourceDescriptor == null) return null;
    return new OccurenceInfo(editSourceDescriptor, counters.myFoundOccurenceNumber, counters.myOccurencesCount);
  }

  @Override
  public OccurenceInfo goPreviousOccurence() {
    Counters counters = new Counters();
    DefaultMutableTreeNode node = findNode(myTree, false, counters);
    if (node == null) return null;
    TreePath treePath = new TreePath(node.getPath());
    TreeUtil.selectPath(myTree, treePath);
    Navigatable editSourceDescriptor = createDescriptorForNode(node);
    if (editSourceDescriptor == null) return null;
    return new OccurenceInfo(editSourceDescriptor, counters.myFoundOccurenceNumber, counters.myOccurencesCount);
  }

  @Override
  public boolean hasNextOccurence() {
    DefaultMutableTreeNode node = findNode(myTree, true, null);
    return node != null;
  }

  @Override
  public boolean hasPreviousOccurence() {
    DefaultMutableTreeNode node = findNode(myTree, false, null);
    return node != null;
  }

  protected static class Counters {
    /**
     * Equals to {@code -1} if this value is unsupported.
     */
    int myFoundOccurenceNumber;
    /**
     * Equals to {@code -1} if this value is unsupported.
     */
    int myOccurencesCount;
  }

  private DefaultMutableTreeNode findNode(@NotNull JTree tree, boolean forward, Counters counters) {
    TreePath selectionPath = tree.getSelectionPath();
    TreeNode selectedNode = null;
    if (selectionPath != null) {
      selectedNode = (TreeNode)selectionPath.getLastPathComponent();
    }
    return findNode(tree, selectedNode, forward, counters);
  }

  public DefaultMutableTreeNode findNode(@NotNull JTree tree, TreeNode selectedNode, boolean forward, Counters counters) {
    boolean[] ready = {selectedNode == null};

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();

    Enumeration enumeration = root.preorderEnumeration();
    List<TreeNode> nodes = new ArrayList<>();
    while (enumeration.hasMoreElements()) {
      TreeNode node = (TreeNode)enumeration.nextElement();
      nodes.add(node);
    }

    DefaultMutableTreeNode result = null;

    if (forward) {
      for (TreeNode node : nodes) {
        DefaultMutableTreeNode nextNode = getNode(node, selectedNode, ready);
        if (nextNode != null) {
          result = nextNode;
          break;
        }
      }
    }
    else {
      for (int i=nodes.size() - 1; i >= 0; i--) {
        TreeNode node = nodes.get(i);
        DefaultMutableTreeNode nextNode = getNode(node, selectedNode, ready);
        if (nextNode != null) {
          result = nextNode;
          break;
        }
      }
    }

    if (result == null) {
      return null;
    }

    if (counters != null) {
      counters.myFoundOccurenceNumber = 0;
      counters.myOccurencesCount = 0;
      for (TreeNode node : nodes) {
        if (!(node instanceof DefaultMutableTreeNode)) continue;

        Navigatable descriptor = createDescriptorForNode((DefaultMutableTreeNode)node);
        if (descriptor == null) continue;

        counters.myOccurencesCount++;
        if (result == node) {
          counters.myFoundOccurenceNumber = counters.myOccurencesCount;
        }
      }
    }

    return result;
  }

  protected DefaultMutableTreeNode getNode(TreeNode node, TreeNode selectedNode, boolean[] ready) {
    if (!ready[0]) {
      if (node == selectedNode) {
        ready[0] = true;
      }
      return null;
    }
    if (!(node instanceof DefaultMutableTreeNode)) return null;

    Navigatable descriptor = createDescriptorForNode((DefaultMutableTreeNode)node);
    if (descriptor == null) return null;
    return (DefaultMutableTreeNode)node;
  }
}
