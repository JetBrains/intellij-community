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
package com.intellij.execution.dashboard;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.TreeAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Action for grouping items in a run dashboard tree.
 *
 * @author konstantin.aleev
 */
public interface RunDashboardGroupingRule extends TreeAction {
  ExtensionPointName<RunDashboardGroupingRule> EP_NAME = ExtensionPointName.create("com.intellij.runDashboardGroupingRule");

  Comparator<RunDashboardGroupingRule> PRIORITY_COMPARATOR = (o1, o2) -> {
    final int res = o2.getPriority() - o1.getPriority();
    return res != 0 ? res : (o1.getName().compareTo(o2.getName()));
  };

  Comparator<RunDashboardGroup> GROUP_NAME_COMPARATOR = Comparator.comparing(RunDashboardGroup::getName);

  /**
   * Grouping rules are ordered and applied to dashboard nodes according to their priority.
   * The higher the priority, the higher groups produced by this rule are presented in the dashboard tree.
   *
   * @return rule's priority.
   */
  int getPriority();

  /**
   * @return {@code true} if grouping rule should always be applied to dashboard nodes.
   */
  boolean isAlwaysEnabled();

  /**
   * @return {@code false} if groups with single node should not added to the dashboard tree keeping such nodes ungrouped.
   */
  boolean shouldGroupSingleNodes();

  /**
   * @param node node which should be grouped by this grouping rule.
   * @return a group which node belongs to or {@code null} if node could not be grouped by this rule.
   */
  @Nullable
  RunDashboardGroup getGroup(AbstractTreeNode<?> node);

  default Comparator<RunDashboardGroup> getGroupComparator() {
    return GROUP_NAME_COMPARATOR;
  }

  interface Priorities {
    int BY_RUN_CONFIG = 200;
    int BY_FOLDER = 400;
    int BY_STATUS = 800;
    int BY_TYPE = 1000;
  }
}
