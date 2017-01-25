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

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author konstantin.aleev
 */
class RunConfigurationNode extends AbstractRunConfigurationNode<RunnerAndConfigurationSettings> {
  private final List<RunDescriptorNode> myChildren;

  public RunConfigurationNode(Project project, @NotNull final RunnerAndConfigurationSettings configurationSettings) {
    super(project, configurationSettings, configurationSettings);

    ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(myProject);
    List<RunContentDescriptor> descriptors = executionManager.getDescriptors(settings -> {
      RunConfiguration configuration = settings.getConfiguration();
      return configuration != null ? configuration.equals(configurationSettings.getConfiguration()) :
             settings.equals(configurationSettings);
    });
    myChildren = descriptors.stream().map(descriptor -> new RunDescriptorNode(myProject, configurationSettings, descriptor))
      .collect(Collectors.toList());
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    if (myChildren.size() == 1) {
      return Collections.emptyList();
    }
    return myChildren;
  }

  @Override
  public boolean isAlwaysExpand() {
    return true;
  }

  @Nullable
  @Override
  protected Icon getIconDecorator() {
    for (RunDescriptorNode node : myChildren) {
      Icon decorator = node.getIconDecorator();
      if (decorator != null) {
        return decorator;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public RunContentDescriptor getDescriptor() {
    if (myChildren.size() == 1) {
      return myChildren.get(0).getDescriptor();
    }
    return null;
  }

  @Override
  public boolean isTerminated() {
    return myChildren.stream().allMatch(RunDescriptorNode::isTerminated);
  }
}
