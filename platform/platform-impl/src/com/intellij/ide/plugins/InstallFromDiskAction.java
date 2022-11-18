// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

class InstallFromDiskAction extends DumbAwareAction {

  private static final @NonNls String PLUGINS_PRESELECTION_PATH = "plugins.preselection.path";

  private final @NotNull InstalledPluginsTableModel myTableModel;
  private final @NotNull PluginEnabler myPluginEnabler;
  private final @Nullable JComponent myParentComponent;

  protected InstallFromDiskAction(@NotNull InstalledPluginsTableModel tableModel,
                                  @NotNull PluginEnabler pluginEnabler,
                                  @Nullable JComponent parentComponent) {
    super(IdeBundle.messagePointer("action.InstallFromDiskAction.text"),
          AllIcons.Nodes.Plugin);
    myTableModel = tableModel;
    myPluginEnabler = pluginEnabler;
    myParentComponent = parentComponent;
  }

  InstallFromDiskAction(@Nullable Project project) {
    this(new InstalledPluginsTableModel(project),
         PluginEnabler.HEADLESS,
         null);
  }

  @SuppressWarnings("unused")
    // called reflectively
  InstallFromDiskAction() {
    this(null);
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

    FileChooser.chooseFile(new FileChooserDescriptorImpl(),
                           project,
                           myParentComponent,
                           getFileToSelect(PropertiesComponent.getInstance().getValue(PLUGINS_PRESELECTION_PATH)),
                           virtualFile -> {
                             File file = VfsUtilCore.virtualToIoFile(virtualFile);
                             PropertiesComponent.getInstance().setValue(PLUGINS_PRESELECTION_PATH,
                                                                        FileUtilRt.toSystemIndependentName(file.getParent()));

                             installFromDisk(file, project);
                           });
  }

  private void installFromDisk(@NotNull File file,
                               @Nullable Project project) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      PluginInstaller.installFromDisk(myTableModel, myPluginEnabler, file, myParentComponent, callbackData -> {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          onPluginInstalledFromDisk(callbackData, project);
        });
      });
    }, IdeBundle.message("action.InstallFromDiskAction.progress.text"), true, project, myParentComponent);
  }

  @RequiresEdt
  protected void onPluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData,
                                           @Nullable Project project) {
    PluginInstaller.installPluginFromCallbackData(callbackData,
                                                  project,
                                                  myParentComponent);
  }

  private static @Nullable VirtualFile getFileToSelect(@Nullable String path) {
    return path != null ?
           VfsUtil.findFileByIoFile(new File(FileUtilRt.toSystemDependentName(path)), false) :
           null;
  }

  private static class FileChooserDescriptorImpl extends FileChooserDescriptor {

    private FileChooserDescriptorImpl() {
      super(false, false, true, true, false, false);
      setTitle(IdeBundle.message("chooser.title.plugin.file"));
      setDescription(IdeBundle.message("chooser.description.jar.and.zip.archives.are.accepted"));
    }

    @Override
    public boolean isFileSelectable(@Nullable VirtualFile file) {
      if (file == null) {
        return false;
      }

      final String extension = file.getExtension();
      return Comparing.strEqual(extension, "jar") || Comparing.strEqual(extension, "zip");
    }
  }
}