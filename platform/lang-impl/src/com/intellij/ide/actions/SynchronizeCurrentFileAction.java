// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SynchronizeCurrentFileAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && localFiles(e).findAny().isPresent());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = getEventProject(e);
    List<VirtualFile> files = localFiles(e).collect(Collectors.toList());
    if (project == null || files.isEmpty()) return;

    for (VirtualFile file : files) {
      if (file.isDirectory()) file.getChildren();
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markClean();
        ((NewVirtualFile)file).markDirtyRecursively();
      }
    }

    RefreshQueue.getInstance().refresh(true, true, () -> postRefresh(project, files), files);
  }

  private static void postRefresh(Project project, List<VirtualFile> files) {
    VcsFacade.getInstance().markFilesDirty(project, files);
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.setInfo(IdeBundle.message("action.sync.completed.successfully"));
    }
  }

  private static Stream<VirtualFile> localFiles(AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return files != null ? Stream.of(files).filter(f -> f.isValid() && f.isInLocalFileSystem()) : Stream.empty();
  }
}
