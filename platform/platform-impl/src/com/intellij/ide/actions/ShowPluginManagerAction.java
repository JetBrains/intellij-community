// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.UIComponentFileEditor;
import com.intellij.ide.plugins.UIComponentVirtualFile;
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum;
import com.intellij.openapi.actionSystem.ActionPlaces;
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

import javax.swing.JComponent;

final class ShowPluginManagerAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PluginManagerOpenSourceEnum openSource = PluginManagerOpenSourceEnum.fromActionPlace(e.getPlace());
    Project project = e.getProject();
    if (project != null && Registry.is("ide.show.plugins.in.editor")) {
      showPluginsInEditor(project, openSource);
      return;
    }
    ShowSettingsUtil.getInstance().showSettingsDialog(
      ProjectUtil.currentOrDefaultProject(project),
      PluginManagerConfigurable.class,
      c -> c.setOpenSource(openSource)
    );
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static void showPluginsInEditor(@NotNull Project project, @NotNull PluginManagerOpenSourceEnum openSource) {
    var file = new PluginVirtualFile(openSource);
    FileEditorManager.getInstance(project).openFile(file, true);
  }

  private static final class PluginVirtualFile extends UIComponentVirtualFile {
    private final PluginManagerOpenSourceEnum openSource;

    PluginVirtualFile(@NotNull PluginManagerOpenSourceEnum openSource) {
      super("Plugins", AllIcons.Nodes.Plugin);
      this.openSource = openSource;
    }

    @Override
    public @NotNull Content createContent(@NotNull UIComponentFileEditor editor) {
      return new ShowPluginManagerAction.Content(openSource);
    }
  }

  private static final class Content implements UIComponentVirtualFile.Content {
    private final PluginManagerOpenSourceEnum openSource;
    PluginManagerConfigurable configurable;

    Content(@NotNull PluginManagerOpenSourceEnum openSource) {
      this.openSource = openSource;
    }

    @Override
    public @NotNull JComponent createComponent() {
      configurable = new PluginManagerConfigurable();
      configurable.setOpenSource(openSource);
      return PluginsTabFactory.createPluginsPanel(configurable);
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent(@NotNull JComponent component) {
      return configurable.getPreferredFocusedComponent();
    }
  }
}
