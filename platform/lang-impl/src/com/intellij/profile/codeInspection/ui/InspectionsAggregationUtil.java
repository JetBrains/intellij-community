/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.profile.codeInspection.ui.inspectionsTree.InspectionConfigTreeNode;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Queue;
import gnu.trove.THashSet;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class InspectionsAggregationUtil {
  public static List<HighlightDisplayKey> getInspectionsKeys(final InspectionConfigTreeNode node) {
    return ContainerUtil.map(getInspectionsNodes(node), node1 -> node1.getKey());
  }

  public static List<InspectionConfigTreeNode> getInspectionsNodes(final InspectionConfigTreeNode node) {
    final Queue<InspectionConfigTreeNode> q = new Queue<>(1);
    q.addLast(node);
    return getInspectionsNodes(q);
  }

  public static List<InspectionConfigTreeNode> getInspectionsNodes(final TreePath[] paths) {
    final Queue<InspectionConfigTreeNode> q = new Queue<>(paths.length);
    for (final TreePath path : paths) {
      if (path != null) {
        q.addLast((InspectionConfigTreeNode)path.getLastPathComponent());
      }
    }
    return getInspectionsNodes(q);
  }

  private static List<InspectionConfigTreeNode> getInspectionsNodes(final Queue<InspectionConfigTreeNode> queue) {
    final Set<InspectionConfigTreeNode> nodes = new THashSet<>();
    while (!queue.isEmpty()) {
      final InspectionConfigTreeNode node = queue.pullFirst();
      if (node.getDescriptors() == null) {
        for (int i = 0; i < node.getChildCount(); i++) {
          final InspectionConfigTreeNode childNode = (InspectionConfigTreeNode) node.getChildAt(i);
          queue.addLast(childNode);
        }
      } else {
        nodes.add(node);
      }
    }
    return new ArrayList<>(nodes);
  }
}
