// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.project.ProjectKt;
import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class SaveAsDirectoryBasedFormatAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (!isConvertableProject(project) || Messages.showOkCancelDialog(project,
                                                                   "Project will be saved and reopened in new Directory-Based format.\nAre you sure you want to continue?",
                                                                   "Save Project to Directory-Based Format",
                                                                   Messages.getWarningIcon()) != Messages.OK) {
      return;
    }

    IProjectStore store = ProjectKt.getStateStore(project);
    String baseDir = PathUtilRt.getParentPath(store.getProjectFilePath());
    File ideaDir = new File(baseDir, Project.DIRECTORY_STORE_FOLDER);
    if ((ideaDir.exists() && ideaDir.isDirectory()) || createDir(ideaDir)) {
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ideaDir);

      store.clearStorages();
      store.setPath(baseDir);
      // closeAndDispose will also force save project
      ProjectUtil.closeAndDispose(project);
      ProjectUtil.openProject(baseDir, null, false);
    }
    else {
      Messages.showErrorDialog(project, String.format("Unable to create '.idea' directory (%s)", ideaDir), "Error Saving Project!");
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
    e.getPresentation().setVisible(isConvertableProject(project));
  }

  private static boolean isConvertableProject(@Nullable Project project) {
    return project != null && !project.isDefault() && !ProjectKt.isDirectoryBased(project);
  }
}
