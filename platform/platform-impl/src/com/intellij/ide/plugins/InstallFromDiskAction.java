// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

final class InstallFromDiskAction extends DumbAwareAction {

  InstallFromDiskAction() {
    super(IdeBundle.messagePointer("action.InstallFromDiskAction.text"),
          AllIcons.Nodes.Plugin);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!PluginManagerFilters.getInstance().allowInstallFromDisk()) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(false);
      presentation.setDescription(IdeBundle.message("action.InstallFromDiskAction.not.allowed.description"));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (!PluginManagerFilters.getInstance().allowInstallFromDisk()) {
      Messages.showErrorDialog(project,
                               IdeBundle.message("action.InstallFromDiskAction.not.allowed.description"),
                               IdeBundle.message("action.InstallFromDiskAction.text"));
      return;
    }

    PluginInstaller.chooseAndInstall(project,
                                     null,
                                     (file, __) -> PluginInstaller.installFromDisk(file, project, null));
  }
}