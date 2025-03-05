// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.tree;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.RunDashboardGroup;
import com.intellij.execution.dashboard.RunDashboardGroupingRule;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author konstantin.aleev
 */
public final class FolderDashboardGroupingRule implements RunDashboardGroupingRule {
  private static final @NonNls String NAME = "FolderDashboardGroupingRule";

  @Override
  public @NotNull String getName() {
    return NAME;
  }

  @Override
  public @Nullable RunDashboardGroup getGroup(AbstractTreeNode<?> node) {
    if (node instanceof RunDashboardRunConfigurationNode) {
      RunnerAndConfigurationSettings configurationSettings = ((RunDashboardRunConfigurationNode)node).getConfigurationSettings();
      String folderName = configurationSettings.getFolderName();
      if (folderName != null) {
        return new FolderDashboardGroup(node.getProject(), folderName, folderName, AllIcons.Nodes.Folder);
      }
    }
    return null;
  }

  public static final class FolderDashboardGroup extends RunDashboardGroupImpl<String> {
    private final Project myProject;

    public FolderDashboardGroup(Project project, String value, String name, Icon icon) {
      super(value, name, icon);
      myProject = project;
    }

    public Project getProject() {
      return myProject;
    }
  }
}
