/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.Consumer;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class DetectedFrameworksTree extends CheckboxTree {
  private final List<DetectedFrameworkDescription> myDetectedFrameworks;
  private final FrameworkDetectionContext myContext;
  private SetupDetectedFrameworksDialog.GroupByOption myGroupByOption;

  public DetectedFrameworksTree(List<DetectedFrameworkDescription> detectedFrameworks,
                                final FrameworkDetectionContext context,
                                SetupDetectedFrameworksDialog.GroupByOption groupByOption) {
    super(new DetectedFrameworksTreeRenderer(), new CheckedTreeNode(null), new CheckPolicy(true, true, true, false));
    myDetectedFrameworks = detectedFrameworks;
    myContext = context;
    myGroupByOption = groupByOption;
    setShowsRootHandles(false);
    setRootVisible(false);
    createNodes();
    TreeUtil.expandAll(this);
  }

  private void createNodes() {
    CheckedTreeNode root = getRoot();
    if (myGroupByOption == SetupDetectedFrameworksDialog.GroupByOption.TYPE) {
      createNodesGroupedByType(root);
    }
    else {
      createNodesGroupedByDirectory(root);
    }
  }

  private void createNodesGroupedByDirectory(CheckedTreeNode root) {
    Map<VirtualFile, FrameworkDirectoryNode> nodes = new HashMap<VirtualFile, FrameworkDirectoryNode>();
    List<DetectedFrameworkNode> externalNodes = new ArrayList<DetectedFrameworkNode>();
    for (DetectedFrameworkDescription framework : myDetectedFrameworks) {
      VirtualFile parent = VfsUtil.getCommonAncestor(framework.getRelatedFiles());
      if (parent != null && !parent.isDirectory()) {
        parent = parent.getParent();
      }

      final DetectedFrameworkNode frameworkNode = new DetectedFrameworkNode(framework, myContext);
      if (parent != null) {
        createDirectoryNodes(parent, nodes).add(frameworkNode);
      }
      else {
        externalNodes.add(frameworkNode);
      }
    }
    for (FrameworkDirectoryNode directoryNode : nodes.values()) {
      if (directoryNode.getParent() == null) {
        root.add(collapseDirectoryNode(directoryNode));
      }
    }
    for (DetectedFrameworkNode node : externalNodes) {
      root.add(node);
    }
  }

  public void processUncheckedNodes(@NotNull final Consumer<DetectedFrameworkTreeNodeBase> consumer) {
    TreeUtil.traverse(getRoot(), new TreeUtil.Traverse() {
      @Override
      public boolean accept(Object node) {
        if (node instanceof DetectedFrameworkTreeNodeBase) {
          consumer.consume((DetectedFrameworkTreeNodeBase)node);
        }
        return true;
      }
    });
  }

  private static FrameworkDirectoryNode collapseDirectoryNode(FrameworkDirectoryNode node) {
    if (node.getChildCount() == 1) {
      final TreeNode child = node.getChildAt(0);
      if (child instanceof FrameworkDirectoryNode) {
        return collapseDirectoryNode((FrameworkDirectoryNode)child);
      }
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      TreeNode child = node.getChildAt(i);
      if (child instanceof FrameworkDirectoryNode) {
        final FrameworkDirectoryNode collapsed = collapseDirectoryNode((FrameworkDirectoryNode)child);
        if (collapsed != child) {
          node.remove(i);
          node.insert(collapsed, i);
        }
      }
    }
    return node;
  }

  @NotNull
  private static FrameworkDirectoryNode createDirectoryNodes(@NotNull VirtualFile dir, @NotNull Map<VirtualFile, FrameworkDirectoryNode> nodes) {
    final FrameworkDirectoryNode node = nodes.get(dir);
    if (node != null) {
      return node;
    }

    final FrameworkDirectoryNode newNode = new FrameworkDirectoryNode(dir);
    nodes.put(dir, newNode);
    final VirtualFile parent = dir.getParent();
    if (parent != null) {
      createDirectoryNodes(parent, nodes).add(newNode);
    }
    return newNode;
  }

  private void createNodesGroupedByType(CheckedTreeNode root) {
    Map<FrameworkType, FrameworkTypeNode> groupNodes = new HashMap<FrameworkType, FrameworkTypeNode>();
    for (DetectedFrameworkDescription framework : myDetectedFrameworks) {
      final FrameworkType type = framework.getFrameworkType();
      FrameworkTypeNode group = groupNodes.get(type);
      if (group == null) {
        group = new FrameworkTypeNode(type);
        groupNodes.put(type, group);
        root.add(group);
      }
      group.add(new DetectedFrameworkNode(framework, myContext));
    }
  }

  private CheckedTreeNode getRoot() {
    return ((CheckedTreeNode)getModel().getRoot());
  }

  public void changeGroupBy(SetupDetectedFrameworksDialog.GroupByOption option) {
    if (myGroupByOption.equals(option)) return;
    myGroupByOption = option;
    getRoot().removeAllChildren();
    createNodes();
    ((DefaultTreeModel)getModel()).nodeStructureChanged(getRoot());
    TreeUtil.expandAll(this);
  }

  private static class DetectedFrameworksTreeRenderer extends CheckboxTreeCellRenderer {
    private DetectedFrameworksTreeRenderer() {
      super(true, false);
    }

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof DetectedFrameworkTreeNodeBase) {
        ((DetectedFrameworkTreeNodeBase)value).renderNode(getTextRenderer());
      }
    }
  }
}
