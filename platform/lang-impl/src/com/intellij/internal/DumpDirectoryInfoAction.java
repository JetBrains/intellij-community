// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

final class DumpDirectoryInfoAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(DumpDirectoryInfoAction.class);

  DumpDirectoryInfoAction() {
    super(ActionsBundle.messagePointer("action.DumpDirectoryInfoAction.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project != null) {
      final DirectoryIndex index = DirectoryIndex.getInstance(project);
      final VirtualFile root = e.getData(CommonDataKeys.VIRTUAL_FILE);
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        final ContentIterator contentIterator = fileOrDir -> {
          LOG.info(fileOrDir.getPath());

          LOG.info(index.getInfoForFile(fileOrDir).toString());
          return true;
        };

        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        if (root != null) {
          fileIndex.iterateContentUnderDirectory(root, contentIterator);
        }
        else {
          fileIndex.iterateContent(contentIterator);
        }
      }, "Dumping directory index", true, project);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }
}
