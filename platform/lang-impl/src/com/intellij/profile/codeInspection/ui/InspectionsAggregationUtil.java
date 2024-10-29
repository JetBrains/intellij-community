// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author Dmitry Batkovich
 */
@ApiStatus.Internal
public final class InspectionsAggregationUtil {
  public static List<HighlightDisplayKey> getInspectionsKeys(@NotNull InspectionConfigTreeNode node) {
    return ContainerUtil.map(getInspectionsNodes(node), node1 -> node1.getKey());
  }

  public static List<InspectionConfigTreeNode.Tool> getInspectionsNodes(@NotNull InspectionConfigTreeNode node) {
    Deque<InspectionConfigTreeNode> q = new ArrayDeque<>(1);
    q.addLast(node);
    return getInspectionsNodes(q);
  }

  public static List<InspectionConfigTreeNode.Tool> getInspectionsNodes(TreePath @Nullable[] paths) {
    if (paths == null) {
      return Collections.emptyList();
    }
    Deque<InspectionConfigTreeNode> q = new ArrayDeque<>(paths.length);
    for (TreePath path : paths) {
      if (path != null) {
        q.addLast((InspectionConfigTreeNode)path.getLastPathComponent());
      }
    }
    return getInspectionsNodes(q);
  }

  private static List<InspectionConfigTreeNode.Tool> getInspectionsNodes(@NotNull Deque<InspectionConfigTreeNode> queue) {
    List<InspectionConfigTreeNode.Tool> nodes = new ArrayList<>();
    while (true) {
      InspectionConfigTreeNode node = queue.pollFirst();
      if (node == null) {
        break;
      }
      if (node instanceof InspectionConfigTreeNode.Group) {
        for (int i = 0; i < node.getChildCount(); i++) {
          final InspectionConfigTreeNode childNode = (InspectionConfigTreeNode)node.getChildAt(i);
          queue.addLast(childNode);
        }
      }
      else {
        nodes.add((InspectionConfigTreeNode.Tool)node);
      }
    }
    return new ArrayList<>(nodes);
  }
}
