// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExcludeFromProjectAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    ProjectFileIndex index = project == null ? null : ProjectFileIndex.SERVICE.getInstance(project);
    JBIterable<VirtualFile> files = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
    boolean enabled = index != null &&
                      files.isNotEmpty() &&
                      files.filter(o -> !index.isInContent(o)).isEmpty();
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
    List<VirtualFile> roots = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
      .filter(o -> index.isInContent(o)).toList();
    AttachDirectoryUtils.excludeEntriesWithUndo(project, roots, true);
  }
}
