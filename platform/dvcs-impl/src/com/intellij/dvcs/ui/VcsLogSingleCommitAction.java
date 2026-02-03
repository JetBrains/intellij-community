// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogCommitSelection;
import com.intellij.vcs.log.VcsLogDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class VcsLogSingleCommitAction<Repo extends Repository> extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    if (selection == null) return;

    CommitId commit = ContainerUtil.getFirstItem(selection.getCommits());
    assert commit != null;
    Repo repository = getRepositoryForRoot(project, commit.getRoot());
    assert repository != null;

    actionPerformed(repository, commit.getHash());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    if (project == null || selection == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    List<CommitId> commits = selection.getCommits();
    if (commits.isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    CommitId commit = ContainerUtil.getFirstItem(commits);
    assert commit != null;
    Repo repository = getRepositoryForRoot(project, commit.getRoot());

    if (repository == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(isVisible(project, repository, commit.getHash()));
    e.getPresentation().setEnabled(commits.size() == 1 && isEnabled(repository, commit.getHash()));
  }

  protected abstract void actionPerformed(@NotNull Repo repository, @NotNull Hash commit);

  protected boolean isEnabled(@NotNull Repo repository, @NotNull Hash commit) {
    return true;
  }

  protected boolean isVisible(final @NotNull Project project, @NotNull Repo repository, @NotNull Hash hash) {
    return !getRepositoryManager(project).isExternal(repository);
  }

  protected abstract @NotNull AbstractRepositoryManager<Repo> getRepositoryManager(@NotNull Project project);

  protected abstract @Nullable Repo getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root);
}
