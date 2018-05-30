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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
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

  @Nullable
  private Navigatable createDescriptorAndCheckCanNavigate(DefaultMutableTreeNode node) {
    Navigatable navigatable = createDescriptorForNode(node);
    if (navigatable == null || !navigatable.canNavigate()) return null;
    return navigatable;
  }

  @Override
  public OccurenceInfo goNextOccurence() {
    Counters counters = new Counters();
    DefaultMutableTreeNode node = findNode(myTree, true, counters);
    if (node == null) return null;
    TreePath treePath = new TreePath(node.getPath());
    TreeUtil.selectPath(myTree, treePath);
    Navigatable editSourceDescriptor = createDescriptorAndCheckCanNavigate(node);
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
    Navigatable editSourceDescriptor = createDescriptorAndCheckCanNavigate(node);
    if (editSourceDescriptor == null) return null;
    return new OccurenceInfo(editSourceDescriptor, counters.myFoundOccurenceNumber, counters.myOccurencesCount);
  }

  @Override
  public boolean hasNextOccurence() {
    return getAllDescriptors().canNavigate(true);
  }

  @Override
  public boolean hasPreviousOccurence() {
    return getAllDescriptors().canNavigate(false);
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
    return findNode(tree, getSelectedNode(tree), forward, counters);
  }

  public DefaultMutableTreeNode findNode(@NotNull JTree tree, TreeNode selectedNode, boolean forward, Counters counters) {
    List<TreeNode> nodes = getAllNodes(tree);

    DefaultMutableTreeNode result = getNextNode(selectedNode, forward, nodes);

    if (counters != null) {
      counters.myFoundOccurenceNumber = 0;
      counters.myOccurencesCount = 0;
      for (TreeNode node : nodes) {
        if (!(node instanceof DefaultMutableTreeNode)) continue;

        Navigatable descriptor = createDescriptorAndCheckCanNavigate((DefaultMutableTreeNode)node);
        if (descriptor == null) continue;

        counters.myOccurencesCount++;
        if (result == node) {
          counters.myFoundOccurenceNumber = counters.myOccurencesCount;
        }
      }
    }

    return result;
  }

  private DefaultMutableTreeNode getNextNode(TreeNode selectedNode, boolean forward, List<TreeNode> nodes) {
    boolean ready = false;
    for (TreeNode node : forward ? nodes : ContainerUtil.reverse(nodes)) {
      if (!ready) {
        if (node == selectedNode) ready = true;
        continue;
      }
      if (node instanceof DefaultMutableTreeNode) {
        Navigatable descriptor = createDescriptorAndCheckCanNavigate((DefaultMutableTreeNode)node);
        if (descriptor != null) {
          return (DefaultMutableTreeNode)node;
        }
      }
    }
    return null;
  }

  @Nullable
  private static TreeNode getSelectedNode(@NotNull JTree tree) {
    TreePath selectionPath = tree.getSelectionPath();
    TreeNode selectedNode = null;
    if (selectionPath != null) {
      selectedNode = (TreeNode)selectionPath.getLastPathComponent();
    }
    return selectedNode;
  }

  public static class Descriptors {
    @NotNull
    private final List<Navigatable> myDescriptors;
    private final int mySelectedPos;

    private Descriptors(@NotNull List<Navigatable> descriptors, int pos) {
      myDescriptors = descriptors;
      mySelectedPos = pos;
    }

    /**
     * Can be called in a non-EDT thread in a read action.
     * To prevent races, all related canNavigate() call should be made
     * in the same read action.
     */
    public boolean canNavigate(boolean forward) {
      ApplicationManager.getApplication().assertReadAccessAllowed();

      if (!(mySelectedPos >= 0 && mySelectedPos < myDescriptors.size())) return false;

      List<Navigatable> subList = forward
                                  ? myDescriptors.subList(mySelectedPos + 1, myDescriptors.size())
                                  : ContainerUtil.reverse(myDescriptors.subList(0, mySelectedPos));

      for (Navigatable descriptor : subList) {
        if (descriptor != null && descriptor.canNavigate()) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * @return descriptors for all nodes (to be able to inspect it later, possibly in a background thread)
   */
  @ApiStatus.Experimental
  @NotNull
  public Descriptors getAllDescriptors() {
    List<TreeNode> nodes = getAllNodes(myTree);
    TreeNode selectedNode = getSelectedNode(myTree);
    List<Navigatable> result = new ArrayList<>(nodes.size());
    int selectedPos = -1;
    for (TreeNode node : nodes) {
      Navigatable descriptor = node instanceof DefaultMutableTreeNode
                               ? createDescriptorForNode((DefaultMutableTreeNode)node)
                               : null;
      if (node == selectedNode) {
        selectedPos = result.size();
      }
      result.add(descriptor);
    }
    return new Descriptors(result, selectedPos);
  }

  @NotNull
  private static List<TreeNode> getAllNodes(@NotNull JTree tree) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();

    Enumeration enumeration = root.preorderEnumeration();
    List<TreeNode> nodes = new ArrayList<>();
    while (enumeration.hasMoreElements()) {
      TreeNode node = (TreeNode)enumeration.nextElement();
      nodes.add(node);
    }
    return nodes;
  }
}
