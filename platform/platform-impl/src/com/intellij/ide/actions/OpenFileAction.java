/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OpenFileAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    @Nullable final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    final boolean showFiles = project != null || PlatformProjectOpenProcessor.getInstanceIfItExists() != null;

    final FileChooserDescriptor descriptor = new OpenProjectFileChooserDescriptor(true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        if (super.isFileSelectable(file)) {
          return true;
        }
        if (file.isDirectory()) {
          return false;
        }
        return showFiles && !FileElement.isArchive(file);
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!file.isDirectory() && isFileSelectable(file)) {
          if (!showHiddenFiles && FileElement.isFileHidden(file)) return false;
          return true;
        }
        return super.isFileVisible(file, showHiddenFiles);
      }

      @Override
      public boolean isChooseMultiple() {
        return showFiles;
      }
    };
    descriptor.setTitle(showFiles ? "Open File or Project" : "Open Project");

    VirtualFile userHomeDir = null;
    if (SystemInfo.isUnix) {
      userHomeDir = VfsUtil.getUserHomeDir();
    }

    descriptor.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, Boolean.TRUE);

    FileChooser.chooseFiles(descriptor, project, userHomeDir, new Consumer<List<VirtualFile>>() {
      @Override
      public void consume(final List<VirtualFile> files) {
        for (VirtualFile file : files) {
          if (!descriptor.isFileSelectable(file)) { // on Mac, it could be selected anyway
            Messages.showInfoMessage(project,
                                     file.getPresentableUrl() + " contains no " +
                                     ApplicationNamesInfo.getInstance().getFullProductName() + " project",
                                     "Cannot Open Project");
            return;
          }
        }
        doOpenFile(project, files);
      }
    });
  }

  private static void doOpenFile(@Nullable final Project project,
                                 @NotNull final List<VirtualFile> result) {
    for (final VirtualFile file : result) {
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

      if (OpenProjectFileChooserDescriptor.isProjectFile(file)) {
        int answer = Messages.showYesNoDialog(project,
                                              IdeBundle.message("message.open.file.is.project", file.getName()),
                                              IdeBundle.message("title.open.project"),
                                              Messages.getQuestionIcon());
        if (answer == 0) {
          FileChooserUtil.setLastOpenedFile(ProjectUtil.openOrImport(file.getPath(), project, false), file);
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

  public static void openFile(final String filePath, final Project project) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
    if (file != null && file.isValid()) {
      openFile(file, project);
    }
  }

  public static void openFile(final VirtualFile virtualFile, final Project project) {
    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    if (editorProviderManager.getProviders(project, virtualFile).length == 0) {
      Messages.showMessageDialog(project,
                                 IdeBundle.message("error.files.of.this.type.cannot.be.opened",
                                                   ApplicationNamesInfo.getInstance().getProductName()),
                                 IdeBundle.message("title.cannot.open.file"),
                                 Messages.getErrorIcon());
      return;
    }

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile);
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

}
