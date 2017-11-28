/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.dashboard.RunDashboardNode;
import com.intellij.execution.dashboard.tree.GroupingNode;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author konstantin.aleev
 */
public abstract class RunDashboardTreeLeafAction<T extends RunDashboardNode> extends RunDashboardTreeActionImpl<T> {
  protected RunDashboardTreeLeafAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected final boolean isMultiSelectionAllowed() {
    return true;
  }

  @Override
  @NotNull
  protected Set<?> collectNodes(@NotNull AbstractTreeBuilder treeBuilder) {
    Set<Object> selectedElement = treeBuilder.getSelectedElements();
    List<AbstractTreeNode> nodes = new ArrayList<>();
    for (Object o : selectedElement) {
      if (!(o instanceof AbstractTreeNode)) {
        return Collections.emptySet();
      }
      nodes.add((AbstractTreeNode)o);
    }
    return getLeaves(nodes);
  }

  private static Set<AbstractTreeNode> getLeaves(Collection<? extends AbstractTreeNode> nodes) {
    Set<AbstractTreeNode> result = new LinkedHashSet<>();
    for (AbstractTreeNode<?> node : nodes) {
      Collection<? extends AbstractTreeNode> children = node.getChildren();
      if (children.isEmpty()) {
        if (!(node instanceof GroupingNode)) {
          // Do not add grouping nodes to the target set
          result.add(node);
        }
      }
      else {
        result.addAll(getLeaves(children));
      }
    }
    return result;
  }
}
