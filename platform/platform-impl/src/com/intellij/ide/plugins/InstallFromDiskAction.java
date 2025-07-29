// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.marketplace.InitSessionResult;
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@ApiStatus.Internal
public class InstallFromDiskAction extends DumbAwareAction {
  private static final String PLUGINS_PRESELECTION_PATH = "plugins.preselection.path";

  private InstalledPluginsTableModel myTableModel;
  private final @NotNull PluginEnabler myPluginEnabler;
  private final @Nullable JComponent myParentComponent;

  @SuppressWarnings({"unused", "ActionPresentationInstantiatedInCtor"})
    // called reflectively
  InstallFromDiskAction() {
    this(null, PluginEnabler.HEADLESS, null);
  }

  @SuppressWarnings("ActionPresentationInstantiatedInCtor")
  protected InstallFromDiskAction(
    @Nullable InstalledPluginsTableModel tableModel,
    @NotNull PluginEnabler pluginEnabler,
    @Nullable JComponent parentComponent
  ) {
    super(IdeBundle.messagePointer("action.InstallFromDiskAction.text"), AllIcons.Nodes.Plugin);
    if (tableModel != null) {
      myTableModel = tableModel;
    }
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
    var file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    var sessionId = UUID.randomUUID();
    //As backend and frontend have their own actions, we don't need a combined state and can use the local one.
    InitSessionResult initSessionResult = DefaultUiPluginManagerController.INSTANCE.initSessionSync(sessionId.toString());
    var tableModel = myTableModel == null ? new InstalledPluginsTableModel(null, initSessionResult, sessionId) : myTableModel;
    installPluginFromDisk(file, project, tableModel, myPluginEnabler, myParentComponent, callbackData -> {
      onPluginInstalledFromDisk(callbackData, project);
    });
  }

  public static void installPluginFromDisk(@Nullable VirtualFile fileToSelect,
                                           @Nullable Project project,
                                           @NotNull InstalledPluginsTableModel tableModel,
                                           @NotNull PluginEnabler pluginEnabler,
                                           @Nullable JComponent parentComponent,
                                           @NotNull Consumer<? super PluginInstallCallbackData> callback) {
    doInstall(fileToSelect, project, tableModel, pluginEnabler, parentComponent, callback);
  }

  private static void doInstall(@Nullable VirtualFile fileToSelect,
                                @Nullable Project project,
                                @NotNull InstalledPluginsTableModel tableModel,
                                @NotNull PluginEnabler pluginEnabler,
                                @Nullable JComponent parentComponent,
                                @NotNull Consumer<? super PluginInstallCallbackData> callback) {
    if (!PluginManagementPolicy.getInstance().isInstallFromDiskAllowed()) {
      var message = IdeBundle.message("action.InstallFromDiskAction.not.allowed.description");
      Messages.showErrorDialog(project, message, IdeBundle.message("action.InstallFromDiskAction.text"));
      return;
    }

    if (fileToSelect == null || !fileToSelect.isInLocalFileSystem() || !hasValidExtension(fileToSelect)) {
      fileToSelect = getFileToSelect(PropertiesComponent.getInstance().getValue(PLUGINS_PRESELECTION_PATH));
    }

    var descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withTitle(IdeBundle.message("install.plugin.chooser.title"))
      .withDescription(IdeBundle.message("install.plugin.chooser.description"))
      .withExtensionFilter(IdeBundle.message("install.plugin.chooser.label"), "zip", "jar");

    var chosenFile = FileChooser.chooseFile(descriptor, parentComponent, project, fileToSelect);
    if (chosenFile != null) {
      PropertiesComponent.getInstance().setValue(PLUGINS_PRESELECTION_PATH, chosenFile.getParent().getPath());
      installPluginFromDisk(chosenFile.toNioPath(), project, tableModel, pluginEnabler, parentComponent, callback);
    }
  }

  private static boolean hasValidExtension(VirtualFile file) {
    String extension = file.getExtension();
    return extension != null && Set.of("zip", "jar").contains(extension.toLowerCase(Locale.ROOT));
  }

  @RequiresEdt
  private static void installPluginFromDisk(Path file,
                                           @Nullable Project project,
                                           @NotNull InstalledPluginsTableModel tableModel,
                                           @NotNull PluginEnabler pluginEnabler,
                                           @Nullable JComponent parentComponent,
                                           @NotNull Consumer<? super PluginInstallCallbackData> callback) {
    PluginInstaller.installFromDisk(tableModel, pluginEnabler, file, project, parentComponent, callback);
  }

  @RequiresEdt
  protected void onPluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData, @Nullable Project project) {
    PluginInstaller.installPluginFromCallbackData(callbackData);
  }

  private static @Nullable VirtualFile getFileToSelect(@Nullable String path) {
    return path != null ? StandardFileSystems.local().findFileByPath(path) : null;
  }
}
