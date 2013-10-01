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

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author spleaner
 */
public class SaveAsDirectoryBasedFormatAction extends AnAction implements DumbAware {

  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project instanceof ProjectEx) {
      final IProjectStore projectStore = ((ProjectEx)project).getStateStore();
      if (StorageScheme.DIRECTORY_BASED != projectStore.getStorageScheme()) {
        final int result = Messages.showOkCancelDialog(project,
                                                       "Project will be saved and reopened in new Directory-Based format.\nAre you sure you want to continue?",
                                                       "Save project to Directory-Based format", Messages.getWarningIcon());
        if (result == 0) {
          final VirtualFile baseDir = project.getBaseDir();
          assert baseDir != null;

          File ideaDir = new File(baseDir.getPath(), ProjectEx.DIRECTORY_STORE_FOLDER + File.separatorChar);
          final boolean ok = (ideaDir.exists() && ideaDir.isDirectory()) || createDir(ideaDir);
          if (ok) {
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ideaDir);


            final StateStorageManager storageManager = projectStore.getStateStorageManager();
            final Collection<String> storageFileNames = new ArrayList<String>(storageManager.getStorageFileNames());
            for (String file : storageFileNames) {
              storageManager.clearStateStorage(file);
            }

            projectStore.setProjectFilePath(baseDir.getPath());
            project.save();
            ProjectUtil.closeAndDispose(project);
            ProjectUtil.openProject(baseDir.getPath(), null, false);
          }
          else {
            Messages.showErrorDialog(project, String.format("Unable to create '.idea' directory (%s)", ideaDir), "Error saving project!");
          }
        }
      }
    }
  }

  private boolean createDir(File ideaDir) {
    try {
      VfsUtil.createDirectories(ideaDir.getPath());
      return true;
    }
    catch (IOException e) {
      return false;
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    boolean visible = project != null;

    if (project instanceof ProjectEx) {
      visible = ((ProjectEx)project).getStateStore().getStorageScheme() != StorageScheme.DIRECTORY_BASED;
    }

    presentation.setVisible(visible);
  }
}
