// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SynchronizeCurrentFileAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && getSupportedFiles(e).findAny().isPresent());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    List<VirtualFile> files = getSupportedFiles(e).collect(Collectors.toList());
    synchronizeFiles(files, project, true);
  }

  public static void synchronizeFiles(Collection<VirtualFile> files, Project project, boolean async) {
    if (files.isEmpty()) return;

    for (VirtualFile file : files) {
      VirtualFileSystem fs = file.getFileSystem();
      if (fs instanceof ArchiveFileSystem) {
        ((ArchiveFileSystem)fs).clearArchiveCache(file);
      }

      if (file.isDirectory()) file.getChildren();
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markClean();
        ((NewVirtualFile)file).markDirtyRecursively();
      }
    }

    RefreshQueue.getInstance().refresh(async, true, () -> postRefresh(project, files), files);
  }

  private static void postRefresh(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    List<VirtualFile> localFiles = ContainerUtil.filter(files, f -> f.isInLocalFileSystem());
    if (!localFiles.isEmpty()) {
      VcsFacade.getInstance().markFilesDirty(project, localFiles);
    }
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.setInfo(IdeBundle.message("action.sync.completed.successfully"));
    }
  }

  private static @NotNull Stream<VirtualFile> getSupportedFiles(AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return files != null ? Stream.of(files).filter(f -> {
      if (!f.isValid()) return false;
      VirtualFileSystem fs = f.getFileSystem();
      return fs instanceof LocalFileSystem || fs instanceof ArchiveFileSystem;
    }) : Stream.empty();
  }
}
