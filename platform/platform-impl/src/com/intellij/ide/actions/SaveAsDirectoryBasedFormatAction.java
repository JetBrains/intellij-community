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

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;

/**
 * @author spleaner
 */
public class SaveAsDirectoryBasedFormatAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || ProjectUtil.isDirectoryBased(project) || Messages.showOkCancelDialog(project,
                                                                                                "Project will be saved and reopened in new Directory-Based format.\nAre you sure you want to continue?",
                                                                                                "Save project to Directory-Based format",
                                                                                                Messages.getWarningIcon()) != Messages.OK) {
      return;
    }

    VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;

    File ideaDir = new File(baseDir.getPath(), Project.DIRECTORY_STORE_FOLDER + File.separatorChar);
    if ((ideaDir.exists() && ideaDir.isDirectory()) || createDir(ideaDir)) {
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ideaDir);

      IProjectStore projectStore = (IProjectStore)ServiceKt.getStateStore(project);
      projectStore.clearStorages();
      projectStore.setPath(baseDir.getPath());
      project.save();
      ProjectUtil.closeAndDispose(project);
      ProjectUtil.openProject(baseDir.getPath(), null, false);
    }
    else {
      Messages.showErrorDialog(project, String.format("Unable to create '.idea' directory (%s)", ideaDir), "Error saving project!");
    }
  }

  private static boolean createDir(File ideaDir) {
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
    Project project = e.getProject();
    e.getPresentation().setVisible(project != null && !ProjectUtil.isDirectoryBased(project));
  }
}
