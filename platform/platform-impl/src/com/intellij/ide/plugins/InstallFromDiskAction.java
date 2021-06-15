// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class InstallFromDiskAction extends DumbAwareAction {

  public InstallFromDiskAction() {
    super(IdeBundle.messagePointer("action.InstallFromDiskAction.text"), AllIcons.Nodes.Plugin);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    PluginInstaller.chooseAndInstall(project,
                                     null,
                                     (file, __) -> PluginInstaller.installFromDisk(file, project, null));
  }
}