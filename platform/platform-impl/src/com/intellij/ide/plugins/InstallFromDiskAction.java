// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;

class InstallFromDiskAction extends DumbAwareAction {
  private static final String PLUGINS_PRESELECTION_PATH = "plugins.preselection.path";

  private final @NotNull InstalledPluginsTableModel myTableModel;
  private final @NotNull PluginEnabler myPluginEnabler;
  private final @Nullable JComponent myParentComponent;

  @SuppressWarnings({"unused", "ActionPresentationInstantiatedInCtor"}) // called reflectively
  InstallFromDiskAction() {
    this(new InstalledPluginsTableModel(null), PluginEnabler.HEADLESS, null);
  }

  @SuppressWarnings("ActionPresentationInstantiatedInCtor")
  protected InstallFromDiskAction(
    @NotNull InstalledPluginsTableModel tableModel,
    @NotNull PluginEnabler pluginEnabler,
    @Nullable JComponent parentComponent
  ) {
    super(IdeBundle.messagePointer("action.InstallFromDiskAction.text"), AllIcons.Nodes.Plugin);
    myTableModel = tableModel;
    myPluginEnabler = pluginEnabler;
    myParentComponent = parentComponent;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!PluginManagementPolicy.getInstance().isInstallFromDiskAllowed()) {
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
    var project = e.getProject();
    if (!PluginManagementPolicy.getInstance().isInstallFromDiskAllowed()) {
      var message = IdeBundle.message("action.InstallFromDiskAction.not.allowed.description");
      Messages.showErrorDialog(project, message, IdeBundle.message("action.InstallFromDiskAction.text"));
      return;
    }

    var contextFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    var toSelect = contextFile != null && contextFile.isInLocalFileSystem() && isPluginArchive(contextFile.getPath())
                   ? contextFile
                   : getFileToSelect(PropertiesComponent.getInstance().getValue(PLUGINS_PRESELECTION_PATH));

    var descriptor = new FileChooserDescriptor(false, false, true, true, false, false)
      .withTitle(IdeBundle.message("chooser.title.plugin.file"))
      .withDescription(IdeBundle.message("chooser.description.jar.and.zip.archives.are.accepted"))
      .withExtensionFilter("", "zip", "jar");

    var chosenFile = FileChooser.chooseFile(descriptor, myParentComponent, project, toSelect);
    if (chosenFile != null) {
      PropertiesComponent.getInstance().setValue(PLUGINS_PRESELECTION_PATH, chosenFile.getParent().getPath());
      installFromDisk(chosenFile.toNioPath(), project);
    }
  }

  @RequiresEdt
  private void installFromDisk(Path file, @Nullable Project project) {
    PluginInstaller.installFromDisk(myTableModel, myPluginEnabler, file, project, myParentComponent, callbackData -> {
      onPluginInstalledFromDisk(callbackData, project);
    });
  }

  @RequiresEdt
  protected void onPluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData, @Nullable Project project) {
    PluginInstaller.installPluginFromCallbackData(callbackData);
  }

  public static boolean isPluginArchive(String filePath) {
    return FileUtilRt.extensionEquals(filePath, "jar") || FileUtilRt.extensionEquals(filePath, "zip");
  }

  private static @Nullable VirtualFile getFileToSelect(@Nullable String path) {
    return path != null ? StandardFileSystems.local().findFileByPath(path) : null;
  }
}
