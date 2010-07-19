/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class OpenFileAction extends AnAction implements DumbAware {
  private static String getLastFilePath(Project project) {
    return PropertiesComponent.getInstance(project).getValue("last_opened_file_path");
  }

  private static void setLastFilePath(Project project, String path) {
    PropertiesComponent.getInstance(project).setValue("last_opened_file_path", path);
  }

  public void actionPerformed(AnActionEvent e) {
    @Nullable final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null &&
        PlatformProjectOpenProcessor.getInstanceIfItExists() == null) {
      return;
    }

    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    ArrayList<FileType> list = new ArrayList<FileType>();
    for(FileType ft: fileTypeManager.getRegisteredFileTypes()) {
      if (fileTypeManager.getAssociatedExtensions(ft).length > 0 &&
          (ft instanceof ProjectFileType || !ft.isReadOnly())) {
        list.add(ft);
      }
    }
    Collections.sort(list, new Comparator<FileType>() {
      public int compare(final FileType o1, final FileType o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });

    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true);
    descriptor.setTitle(IdeBundle.message("title.open.file"));

    final String lastFilePath = project != null ? getLastFilePath(project) : null;
    final VirtualFile toSelect = lastFilePath == null ? null : LocalFileSystem.getInstance().findFileByPath(lastFilePath);

    FileChooser.chooseFilesWithSlideEffect(descriptor, project, toSelect,new Consumer<VirtualFile[]>() {
      @Override
      public void consume(final VirtualFile[] files) {
        doOpenFile(project, files);
      }
    });
  }

  private void doOpenFile(@Nullable final Project project,
                          @NotNull final VirtualFile[] result) {
    if (result.length == 0) return;

    for (final VirtualFile file : result) {
      if (project != null) setLastFilePath(project, file.getParent().getPath());
      if (isProjectFile(file.getName())) {
        int answer = Messages.showYesNoDialog(project,
                                              IdeBundle.message("message.open.file.is.project", file.getName(),
                                                                ApplicationNamesInfo.getInstance().getProductName()),
                                              IdeBundle.message("title.open.project"),
                                              Messages.getQuestionIcon());
        if (answer == 0) {
          ProjectUtil.openProject(file.getPath(), project, false);
          return;
        }
      }

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(file.getName());
      if (type == null) return;

      if (project != null) {
        openFile(file, project);
      } else {
        PlatformProjectOpenProcessor.getInstance().doOpenProject(file, null, false);
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

  public static boolean isProjectFile(final String file) {
    return FileTypeManager.getInstance().getFileTypeByFileName(file) instanceof ProjectFileType;
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null ||
                            PlatformProjectOpenProcessor.getInstanceIfItExists() != null);
  }
}
