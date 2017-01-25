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
import com.intellij.execution.dashboard.DashboardGroup;
import com.intellij.execution.dashboard.DashboardGroupingRule;
import com.intellij.execution.dashboard.DashboardRunConfigurationNode;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.ActionPresentation;
import com.intellij.ide.util.treeView.smartTree.ActionPresentationData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    return new ActionPresentationData(ExecutionBundle.message("runtime.dashboard.group.by.status.action.name"),
                                      ExecutionBundle.message("runtime.dashboard.group.by.status.action.name"),
                                      AllIcons.Actions.GroupByPrefix); // TODO [konstantin.aleev] provide new icon
  }

  @NotNull
  @Override
  public List<DashboardGroup> getPermanentGroups() {
    return Arrays.stream(Status.values()).map(Status::getGroup).collect(Collectors.toList());
  }

  @Nullable
  @Override
  public DashboardGroup getGroup(AbstractTreeNode<?> node) {
    if (node instanceof DashboardRunConfigurationNode) {
      if (((DashboardRunConfigurationNode)node).isTerminated()) {
        return Status.STOPPED.getGroup();
      } else {
        return Status.STARTED.getGroup();
      }
    }
    return null;
  }

  public enum Status {
    STARTED(ExecutionBundle.message("runtime.dashboard.started.group.name"), AllIcons.Toolwindows.ToolWindowRun),
    STOPPED(ExecutionBundle.message("runtime.dashboard.stopped.group.name"), AllIcons.Actions.Suspend);

    private final String myLabel;
    private final Icon myIcon;

    Status(String label, Icon icon) {
      myLabel = label;
      myIcon = icon;
    }

    public DashboardGroup getGroup() {
      return new DashboardGroupImpl<>(this, myLabel, myIcon);
    }
  }
}
