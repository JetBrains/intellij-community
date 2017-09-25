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

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.RunDashboardContributor;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * @author konstantin.aleev
 */
class RunConfigurationNode extends AbstractTreeNode<Pair<RunnerAndConfigurationSettings, Content>>
  implements RunDashboardRunConfigurationNode {

  @Nullable private final RunDashboardContributor myContributor;
  private final UserDataHolder myUserDataHolder = new UserDataHolderBase();

  RunConfigurationNode(Project project, @NotNull Pair<RunnerAndConfigurationSettings, RunContentDescriptor> value,
                       @Nullable RunDashboardContributor contributor) {
    super(project, Pair.create(value.first, value.second == null ? null : value.second.getAttachedContent()));
    myContributor = contributor;
  }

  @Override
  @NotNull
  public RunnerAndConfigurationSettings getConfigurationSettings() {
    //noinspection ConstantConditions ???
    return getValue().first;
  }

  @Nullable
  @Override
  public RunContentDescriptor getDescriptor() {
    Content content = getContent();
    if (content == null) return null;

    return RunContentManagerImpl.getRunContentDescriptorByContent(content);
  }

  @Nullable
  @Override
  public Content getContent() {
    //noinspection ConstantConditions ???
    return getValue().second;
  }

  @Override
  protected void update(PresentationData presentation) {
    RunnerAndConfigurationSettings configurationSettings = getConfigurationSettings();
    //noinspection ConstantConditions
    boolean isStored = RunManager.getInstance(getProject()).hasSettings(configurationSettings);
    presentation.addText(configurationSettings.getName(),
                         isStored ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES);
    Icon icon = null;
    RunDashboardRunConfigurationStatus status = myContributor != null ? myContributor.getStatus(this) :
                                                RunDashboardRunConfigurationStatus.getStatus(this);
    if (RunDashboardRunConfigurationStatus.STARTED.equals(status)) {
      icon = getExecutorIcon();
    }
    else if (RunDashboardRunConfigurationStatus.FAILED.equals(status)) {
      icon = status.getIcon();
    }
    if (icon == null) {
      icon = RunManagerEx.getInstanceEx(getProject()).getConfigurationIcon(configurationSettings);
    }
    presentation.setIcon(isStored ? icon : IconLoader.getDisabledIcon(icon));

    if (myContributor != null) {
      myContributor.updatePresentation(presentation, this);
    }
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myUserDataHolder.putUserData(key, value);
  }

  @Nullable
  @Override
  public RunDashboardContributor getContributor() {
    return myContributor;
  }

  @Nullable
  private Icon getExecutorIcon() {
    Content content = getContent();
    if (content != null) {
      if (!RunContentManagerImpl.isTerminated(content)) {
        Executor executor = RunContentManagerImpl.getExecutorByContent(content);
        if (executor != null) {
          return executor.getIcon();
        }
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return getConfigurationSettings().getName();
  }
}
