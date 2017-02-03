/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.DashboardGroup;
import com.intellij.execution.dashboard.DashboardGroupingRule;
import com.intellij.execution.dashboard.RuntimeDashboardContributor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author konstantin.aleev
 */
public class RuntimeDashboardTreeStructure extends AbstractTreeStructureBase {
  private final Project myProject;
  private final List<DashboardGrouper> myGroupers;
  private final RunConfigurationsTreeRootNode myRootElement;

  public RuntimeDashboardTreeStructure(@NotNull Project project, @NotNull List<DashboardGrouper> groupers) {
    super(project);
    myProject = project;
    myGroupers = groupers;
    myRootElement = new RunConfigurationsTreeRootNode();
  }

  @Nullable
  @Override
  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  @Override
  public Object getRootElement() {
    return myRootElement;
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  public class RunConfigurationsTreeRootNode extends AbstractTreeNode<Object> {
    public RunConfigurationsTreeRootNode() {
      super(RuntimeDashboardTreeStructure.this.myProject, new Object());
    }

    @NotNull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      List<RunConfigurationNode> nodes = new ArrayList<>();

      List<RunnerAndConfigurationSettings> configurations = RunManager.getInstance(myProject).getAllSettings().stream()
        .filter(runConfiguration -> RuntimeDashboardContributor.isShowInDashboard(runConfiguration.getType()))
        .collect(Collectors.toList());

      //noinspection ConstantConditions ???
      ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(myProject);
      configurations.forEach(configurationSettings -> {
        List<RunContentDescriptor> descriptors = executionManager.getDescriptors(settings -> {
          RunConfiguration configuration = settings.getConfiguration();
          return configuration != null ? configuration.equals(configurationSettings.getConfiguration()) :
                 settings.equals(configurationSettings);
        });
        if (descriptors.isEmpty()) {
          nodes.add(new RunConfigurationNode(myProject, configurationSettings, null));
        } else {
          descriptors.forEach(descriptor -> nodes.add(new RunConfigurationNode(myProject, configurationSettings, descriptor)));
        }
      });
      return group(myProject,
                   this,
                   myGroupers.stream().filter(DashboardGrouper::isEnabled).map(DashboardGrouper::getRule).collect(Collectors.toList()),
                   nodes);
    }

    @Override
    protected void update(PresentationData presentation) {
    }
  }

  private static Collection<? extends AbstractTreeNode> group(final Project project, final AbstractTreeNode parent,
                                                              List<DashboardGroupingRule> rules, List<RunConfigurationNode> nodes) {
    if (rules.isEmpty()) {
      return nodes;
    }
    final List<DashboardGroupingRule> remaining = new ArrayList<>(rules);
    DashboardGroupingRule rule = remaining.remove(0);
    Map<DashboardGroup, List<RunConfigurationNode>> groups = nodes.stream().collect(
      HashMap::new,
      (map, node) -> map.computeIfAbsent(rule.getGroup(node), key -> new ArrayList<>()).add(node),
      (firstMap, secondMap) -> firstMap.forEach((key, value) -> value.addAll(secondMap.get(key)))
    );
    final List<AbstractTreeNode> result = new ArrayList<>();
    final List<AbstractTreeNode> ungroupedNodes = new ArrayList<>();
    groups.forEach((group, groupedNodes) -> {
      if (group == null || (!rule.shouldGroupSingleNodes() && groupedNodes.size() == 1)) {
        ungroupedNodes.addAll(group(project, parent, remaining, groupedNodes));
      } else {
        GroupingNode node = new GroupingNode(project, parent.getValue(), group);
        node.addChildren(group(project, node, remaining, groupedNodes));
        result.add(node);
      }
    });

    Collections.sort(result, Comparator.comparing(node -> ((GroupingNode)node).getGroup().getName()));
    result.addAll(ungroupedNodes);
    return result;
  }
}
