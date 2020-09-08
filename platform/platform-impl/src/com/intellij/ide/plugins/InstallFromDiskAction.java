// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class InstallFromDiskAction extends DumbAwareAction {

  public InstallFromDiskAction() {
    super(IdeBundle.messagePointer("action.InstallFromDiskAction.text"), AllIcons.Nodes.Plugin);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PluginInstaller.chooseAndInstall(
      new InstalledPluginsTableModel(e.getProject()),
      null,
      PluginInstallCallbackDataKt::installPluginFromCallbackData
    );
  }
}