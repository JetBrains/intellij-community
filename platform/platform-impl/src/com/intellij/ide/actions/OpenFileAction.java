/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OpenFileAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final boolean showFiles = project != null || PlatformProjectOpenProcessor.getInstanceIfItExists() != null;
    final FileChooserDescriptor descriptor = showFiles ? new ProjectOrFileChooserDescriptor() : new ProjectOnlyFileChooserDescriptor();
    descriptor.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, showFiles);

    FileChooser.chooseFiles(descriptor, project, VfsUtil.getUserHomeDir(), new Consumer<List<VirtualFile>>() {
      @Override
      public void consume(final List<VirtualFile> files) {
        for (VirtualFile file : files) {
          if (!descriptor.isFileSelectable(file)) {
            String message = IdeBundle.message("error.dir.contains.no.project", file.getPresentableUrl());
            Messages.showInfoMessage(project, message, IdeBundle.message("title.cannot.open.project"));
            return;
          }
        }
        doOpenFile(project, files);
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.getPresentation().setIcon(AllIcons.Welcome.OpenProject);
    }
  }

  private static void doOpenFile(@Nullable Project project, @NotNull List<VirtualFile> result) {
    for (VirtualFile file : result) {
      if (file.isDirectory()) {
        Project openedProject;
        if (ProjectAttachProcessor.canAttachToProject()) {
          openedProject = PlatformProjectOpenProcessor.doOpenProject(file, project, false, -1, null, false);
        }
        else {
          openedProject = ProjectUtil.openOrImport(file.getPath(), project, false);
        }
        FileChooserUtil.setLastOpenedFile(openedProject, file);
        return;
      }

      // try to open as a project - unless the file is an .ipr of the current one
      if ((project == null || !file.equals(project.getProjectFile())) && OpenProjectFileChooserDescriptor.isProjectFile(file)) {
        Project openedProject = ProjectUtil.openOrImport(file.getPath(), project, false);
        if (openedProject != null) {
          FileChooserUtil.setLastOpenedFile(openedProject, file);
          return;
        }
      }

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(file, project);
      if (type == null) return;

      if (project != null) {
        openFile(file, project);
      }
      else {
        PlatformProjectOpenProcessor processor = PlatformProjectOpenProcessor.getInstanceIfItExists();
        if (processor != null) {
          processor.doOpenProject(file, null, false);
        }
      }
    }
  }

  public static void openFile(String filePath, @NotNull Project project) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (file != null && file.isValid()) {
      openFile(file, project);
    }
  }

  public static void openFile(VirtualFile file, @NotNull Project project) {
    FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, file);
    if (providers.length == 0) {
      String message = IdeBundle.message("error.files.of.this.type.cannot.be.opened", ApplicationNamesInfo.getInstance().getProductName());
      Messages.showErrorDialog(project, message, IdeBundle.message("title.cannot.open.file"));
      return;
    }

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  private static class ProjectOnlyFileChooserDescriptor extends OpenProjectFileChooserDescriptor {
    public ProjectOnlyFileChooserDescriptor() {
      super(true);
      setTitle(IdeBundle.message("title.open.project"));
    }
  }

  // vanilla OpenProjectFileChooserDescriptor only accepts project files; this one is overridden to accept any files
  private static class ProjectOrFileChooserDescriptor extends OpenProjectFileChooserDescriptor {
    private final FileChooserDescriptor myStandardDescriptor =
      FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withHideIgnored(false);

    public ProjectOrFileChooserDescriptor() {
      super(true);
      setTitle(IdeBundle.message("title.open.file.or.project"));
    }

    @Override
    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      return file.isDirectory() ? super.isFileVisible(file, showHiddenFiles) : myStandardDescriptor.isFileVisible(file, showHiddenFiles);
    }

    @Override
    public boolean isFileSelectable(VirtualFile file) {
      return file.isDirectory() ? super.isFileSelectable(file) : myStandardDescriptor.isFileSelectable(file);
    }

    @Override
    public boolean isChooseMultiple() {
      return true;
    }
  }
}
