// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

@ApiStatus.Internal
public class VcsPushAction extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    VcsRepositoryManager manager = VcsRepositoryManager.getInstance(project);
    Collection<Repository> repositories = e.getData(CommonDataKeys.EDITOR) != null
                                          ? ContainerUtil.emptyList()
                                          : collectRepositories(manager, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
    Repository selectedRepo = DvcsUtil.guessRepositoryForOperation(project, e.getDataContext());
    new VcsPushDialog(project, DvcsUtil.sortRepositories(repositories), selectedRepo).show();
  }

  private static @NotNull Collection<Repository> collectRepositories(@NotNull VcsRepositoryManager vcsRepositoryManager,
                                                                     VirtualFile @Nullable [] files) {
    if (files == null) return Collections.emptyList();
    Collection<Repository> repositories = new HashSet<>();
    for (VirtualFile file : files) {
      Repository repo = vcsRepositoryManager.getRepositoryForFileQuick(file);
      if (repo != null) {
        repositories.add(repo);
      }
    }
    return repositories;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation()
      .setEnabledAndVisible(project != null && !VcsRepositoryManager.getInstance(project).getRepositories().isEmpty());
  }
}
