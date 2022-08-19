// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IncludeToProjectAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    ProjectFileIndex index = project == null ? null : ProjectFileIndex.getInstance(project);
    JBIterable<VirtualFile> files = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
    boolean enabled = index != null &&
                      files.isNotEmpty() &&
                      files.filter(o -> !isExclusionRoot(index, o)).isEmpty();
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean isExclusionRoot(ProjectFileIndex index, VirtualFile o) {
    if (!index.isExcluded(o)) return false;
    VirtualFile parent = o.getParent();
    return parent == null || !index.isExcluded(parent);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    List<VirtualFile> roots = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
      .filter(o -> isExclusionRoot(index, o)).toList();
    AttachDirectoryUtils.excludeEntriesWithUndo(project, roots, false);
  }
}
