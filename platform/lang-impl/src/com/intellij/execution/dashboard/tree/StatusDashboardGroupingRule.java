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
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.dashboard.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author konstantin.aleev
 */
public class StatusDashboardGroupingRule implements DashboardGroupingRule {
  @NonNls private static final String NAME = "StatusDashboardGroupingRule";

  @Override
  @NotNull
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(ExecutionBundle.message("run.dashboard.group.by.status.action.name"),
                                      ExecutionBundle.message("run.dashboard.group.by.status.action.name"),
                                      AllIcons.Actions.GroupByPrefix); // TODO [konstantin.aleev] provide new icon
  }

  @Override
  public int getPriority() {
    return Priorities.BY_STATUS;
  }

  @Override
  public boolean isAlwaysEnabled() {
    return false;
  }

  @Override
  public boolean shouldGroupSingleNodes() {
    return true;
  }

  @Nullable
  @Override
  public DashboardGroup getGroup(AbstractTreeNode<?> node) {
    if (node instanceof DashboardRunConfigurationNode) {
      DashboardRunConfigurationNode runConfigurationNode = (DashboardRunConfigurationNode)node;
      RunDashboardContributor contributor = RunDashboardManager.getInstance(runConfigurationNode.getProject()).getContributor(
        runConfigurationNode.getConfigurationSettings().getType());
      if (contributor != null) {
        DashboardRunConfigurationStatus status = contributor.getStatus(runConfigurationNode);
        return new DashboardGroupImpl<>(status, status.getName(), status.getIcon());
      }
    }
    return null;
  }
}
