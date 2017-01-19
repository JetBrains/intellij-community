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
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.dashboard.Group;
import com.intellij.execution.dashboard.GroupingRule;
import com.intellij.execution.dashboard.DashboardRunConfigurationNode;
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
public class ConfigurationTypeGroupingRule implements GroupingRule {
  @NonNls private static final String NAME = "TypeGroupingRule";

  @Override
  @NotNull
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(ExecutionBundle.message("runtime.dashboard.group.by.type.action.name"),
                                      ExecutionBundle.message("runtime.dashboard.group.by.type.action.name"),
                                      AllIcons.Actions.GroupByFile);
  }

  @Nullable
  @Override
  public Group getGroup(AbstractTreeNode<?> node) {
    if (node instanceof DashboardRunConfigurationNode) {
      RunnerAndConfigurationSettings configurationSettings = ((DashboardRunConfigurationNode)node).getConfigurationSettings();
      ConfigurationType type = configurationSettings.getType();
      if (type != null) {
        return new GroupImpl<>(type, type.getDisplayName(), type.getIcon());
      }
    }
    return null;
  }
}
