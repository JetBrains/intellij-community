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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.NativeFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.util.io.FileTypeFilter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import java.io.File;
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
    @Nullable Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null &&
        PlatformProjectOpenProcessor.getInstanceIfItExists() == null) {
      return;
    }

    String lastFilePath = project != null ? getLastFilePath(project):"";
    //TODO String path = lastFilePath != null ? lastFilePath : RecentProjectsManager.getInstance().getLastProjectPath();
    JFileChooser fileChooser = new JFileChooser(lastFilePath);
    FileView fileView = new FileView() {
      public Icon getIcon(File f) {
        if (f.isDirectory()) return super.getIcon(f);
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
        if (fileType == UnknownFileType.INSTANCE || fileType == NativeFileType.INSTANCE) {
          return super.getIcon(f);
        }
        return fileType.getIcon();
      }
    };
    fileChooser.setFileView(fileView);
    fileChooser.setMultiSelectionEnabled(true);
    fileChooser.setAcceptAllFileFilterUsed(false);
    fileChooser.setDialogTitle(IdeBundle.message("title.open.file"));

    FileFilter allFilesFilter = new FileFilter() {
      public boolean accept(File f) {
        return true;
      }

      public String getDescription() {
        return IdeBundle.message("filter.all.file.types");
      }
    };

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
    for(FileType ft: list) {
      fileChooser.addChoosableFileFilter(new FileTypeFilter(ft));
    }
    fileChooser.addChoosableFileFilter(allFilesFilter);

    fileChooser.setFileFilter(allFilesFilter);

    if (fileChooser.showOpenDialog(WindowManager.getInstance().suggestParentWindow(project)) !=
        JFileChooser.APPROVE_OPTION) {
      return;
    }
    File [] files = fileChooser.getSelectedFiles();
    if (files == null) return;

    for (File file : files) {
      if (project != null) setLastFilePath(project, file.getParent());
      if (isProjectFile(file)) {
        int answer = Messages.showYesNoDialog(project,
                                              IdeBundle.message("message.open.file.is.project", file.getName(),
                                                                ApplicationNamesInfo.getInstance().getProductName()),
                                              IdeBundle.message("title.open.project"),
                                              Messages.getQuestionIcon());
        if (answer == 0) {
          ProjectUtil.openProject(file.getAbsolutePath(), project, false);
          return;
        }
      }

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(file.getName());
      if (type == null) return;

      String absolutePath = file.getAbsolutePath();

      if (project != null) {
        openFile(absolutePath, project);
      } else {
        VirtualFile vfile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (vfile != null) {
          PlatformProjectOpenProcessor.getInstance().doOpenProject(vfile, null, false);
        }
      }
    }
  }

  public static void openFile(String absolutePath, final Project project) {
    final String correctPath = absolutePath.replace(File.separatorChar, '/');
    final VirtualFile[] virtualFiles = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        virtualFiles[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(correctPath);
      }
    });
    
    if (virtualFiles[0] == null) {
      Messages.showErrorDialog(project, IdeBundle.message("error.file.does.not.exist", absolutePath), IdeBundle.message("title.cannot.open.file"));
      return;
    }

    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    if (editorProviderManager.getProviders(project, virtualFiles[0]).length == 0) {
      Messages.showMessageDialog(project,
                                 IdeBundle.message("error.files.of.this.type.cannot.be.opened",
                                                   ApplicationNamesInfo.getInstance().getProductName()),
                                 IdeBundle.message("title.cannot.open.file"),
                                 Messages.getErrorIcon());
      return;
    }

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFiles[0]);
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static boolean isProjectFile(File file) {
    return FileTypeManager.getInstance().getFileTypeByFileName(file.getName()) instanceof ProjectFileType;
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null ||
                            PlatformProjectOpenProcessor.getInstanceIfItExists() != null);
  }
}
