// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.codeInsight.actions.VcsFacade;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
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
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && supportedFiles(e).findAny().isPresent());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    List<VirtualFile> files = supportedFiles(e).collect(Collectors.toList());
    synchronizeFiles(files, project, true);
  }

  public static void synchronizeFiles(@NotNull Collection<VirtualFile> files, @NotNull Project project, boolean async) {
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

  private static void postRefresh(Project project, Collection<? extends VirtualFile> files) {
    List<VirtualFile> localFiles = ContainerUtil.filter(files, f -> f.isInLocalFileSystem());
    if (!localFiles.isEmpty()) {
      VcsFacade.getInstance().markFilesDirty(project, localFiles);
    }
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.setInfo(IdeBundle.message("action.sync.completed.successfully"));
    }
  }

  private static Stream<VirtualFile> supportedFiles(AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    return files == null ? Stream.empty() : Stream.of(files)
      .filter(f -> f.isValid() && (f.getFileSystem() instanceof LocalFileSystem || f.getFileSystem() instanceof ArchiveFileSystem));
  }
}
