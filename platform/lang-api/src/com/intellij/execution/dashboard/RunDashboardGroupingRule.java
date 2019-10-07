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
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Action for grouping items in a run dashboard (services) tree.
 * Grouping rules are applied to dashboard nodes according to their order defined in plug-in configuration.
 */
public interface RunDashboardGroupingRule {
  /**
   * @param node node which should be grouped by this grouping rule.
   * @return a group which node belongs to or {@code null} if node could not be grouped by this rule.
   */
  @Nullable
  RunDashboardGroup getGroup(AbstractTreeNode<?> node);

  /**
   * Returns a unique identifier for the rule.
   *
   * @return the rule identifier.
   */
  @NotNull
  String getName();

  /**
   * @deprecated not applicable for Services tool window
   * @return {@code true} if grouping rule should always be applied to dashboard nodes.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  default boolean isAlwaysEnabled() {
    return false;
  }

  /**
   * @deprecated not applicable for Services tool window
   * @return {@code false} if groups with single node should not added to the dashboard tree keeping such nodes ungrouped.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  default boolean shouldGroupSingleNodes() {
    return true;
  }

  /**
   * @deprecated not applicable for Services tool window
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  Comparator<RunDashboardGroup> GROUP_NAME_COMPARATOR = Comparator.comparing(RunDashboardGroup::getName);

  /**
   * @deprecated not applicable for Services tool window
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  default Comparator<RunDashboardGroup> getGroupComparator() {
    return GROUP_NAME_COMPARATOR;
  }

  /**
   * @deprecated not applicable for Services tool window
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @NotNull
  default ActionPresentation getPresentation() {
    return new ActionPresentationData("", "", null);
  }
}
