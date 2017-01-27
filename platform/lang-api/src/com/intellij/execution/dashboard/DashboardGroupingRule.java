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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Action for grouping items in a runtime dashboard tree.
 *
 * @author konstantin.aleev
 */
public interface DashboardGroupingRule extends TreeAction {
  ExtensionPointName<DashboardGroupingRule> EP_NAME = ExtensionPointName.create("com.intellij.runtimeDashboardGroupingRule");

  Comparator<DashboardGroupingRule> PRIORITY_COMPARATOR = (o1, o2) -> {
    final int res = o2.getPriority() - o1.getPriority();
    return res != 0 ? res : (o1.getName().compareTo(o2.getName()));
  };

  /**
   * Grouping rules are ordered and applied to dashboard nodes according to their priority.
   * @return Rule's priority.
   */
  int getPriority();

  /**
   * @return {@code true} if grouping rule should always be applied to dashboard nodes.
   */
  boolean isAlwaysEnable();

  /**
   * @return A list of groups which should be shown in the tree even if they do not contain any nodes.
   */
  @NotNull
  default List<DashboardGroup> getPermanentGroups() {
    return Collections.emptyList();
  }

  /**
   * @param node Node which should be grouped by this grouping rule.
   * @return A group which node belongs to or null if node could not be grouped by this rule.
   */
  @Nullable
  DashboardGroup getGroup(AbstractTreeNode<?> node);

  interface Priorities {
    int BY_FOLDER = 200;
    int BY_STATUS = 800;
    int BY_TYPE = 1000;
  }
}
