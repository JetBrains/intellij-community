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
import com.intellij.execution.dashboard.DashboardNode;
import com.intellij.execution.dashboard.RuntimeDashboardContributor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.ui.RowIcon;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author konstantin.aleev
 */
abstract class AbstractRunConfigurationNode<T> extends AbstractTreeNode<T> implements DashboardNode {
  @NotNull private final RunnerAndConfigurationSettings myConfigurationSettings;

  protected AbstractRunConfigurationNode(Project project, T value, @NotNull RunnerAndConfigurationSettings configurationSettings) {
    super(project, value);
    myConfigurationSettings = configurationSettings;
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setPresentableText(myConfigurationSettings.getName());
    Icon icon = myConfigurationSettings.getConfiguration().getIcon();
    Icon decorator = getIconDecorator();
    if (decorator != null) {
      icon = new RowIcon(icon, decorator);
    }
    presentation.setIcon(icon);
    RuntimeDashboardContributor contributor = RuntimeDashboardContributor.getContributor(myConfigurationSettings.getType());
    if (contributor != null) {
      contributor.updatePresentation(presentation, this);
    }
  }

  @Override
  @Nullable
  public Content getContent() {
    return getDescriptor() == null ? null : getDescriptor().getAttachedContent();
  }

  @Nullable
  protected abstract Icon getIconDecorator();

  @Nullable
  protected abstract RunContentDescriptor getDescriptor();
}
