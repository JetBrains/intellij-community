// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.UIComponentFileEditor;
import com.intellij.ide.plugins.UIComponentVirtualFile;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.welcomeScreen.PluginsTabFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class ShowPluginManagerAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && Registry.is("ide.show.plugins.in.editor")) {
      showPluginsInEditor(project);
      return;
    }
    ShowSettingsUtil.getInstance().showSettingsDialog(
      ProjectUtil.currentOrDefaultProject(project),
      PluginManagerConfigurable.class
    );
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static void showPluginsInEditor(@NotNull Project project) {
    var file = new PluginVirtualFile();
    FileEditorManager.getInstance(project).openFile(file, true);
  }

  private static final class PluginVirtualFile extends UIComponentVirtualFile {
    PluginVirtualFile() {
      super("Plugins", AllIcons.Nodes.Plugin);
    }

    @Override
    public @NotNull Content createContent(@NotNull UIComponentFileEditor editor) {
      return new ShowPluginManagerAction.Content();
    }
  }

  private static final class Content implements UIComponentVirtualFile.Content {
    PluginManagerConfigurable configurable;

    @Override
    public @NotNull JComponent createComponent() {
      configurable = new PluginManagerConfigurable();
      return PluginsTabFactory.createPluginsPanel(configurable);
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent(@NotNull JComponent component) {
      return configurable.getPreferredFocusedComponent();
    }
  }
}
