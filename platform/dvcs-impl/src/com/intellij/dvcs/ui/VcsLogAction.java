// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static com.intellij.vcs.log.util.VcsLogUtil.MAX_SELECTED_COMMITS;

public abstract class VcsLogAction<Repo extends Repository> extends DumbAwareAction {
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

    selection.requestFullDetails(details -> {
      MultiMap<Repo, VcsFullCommitDetails> grouped = groupCommits(project, details, VcsShortCommitDetails::getRoot);
      if (grouped == null) return;
      actionPerformed(project, grouped);
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLogCommitSelection selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION);
    if (project == null || selection == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    MultiMap<Repo, Hash> grouped = groupFirstPackOfCommits(project, selection);
    if (grouped == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setVisible(isVisible(project, grouped));
      e.getPresentation().setEnabled(!grouped.isEmpty() && isEnabled(grouped));
    }
  }

  protected abstract void actionPerformed(@NotNull Project project, @NotNull MultiMap<Repo, VcsFullCommitDetails> grouped);

  protected abstract boolean isEnabled(@NotNull MultiMap<Repo, Hash> grouped);

  protected boolean isVisible(@NotNull Project project, @NotNull MultiMap<Repo, Hash> grouped) {
    RepositoryManager<Repo> manager = getRepositoryManager(project);
    return grouped.keySet().stream().noneMatch(manager::isExternal);
  }

  protected abstract @NotNull AbstractRepositoryManager<Repo> getRepositoryManager(@NotNull Project project);

  protected abstract @Nullable Repo getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root);

  /**
   * Collects no more than VcsLogUtil.MAX_SELECTED_COMMITS and groups them by repository.
   * To use only during update.
   */
  private @Nullable MultiMap<Repo, Hash> groupFirstPackOfCommits(@NotNull Project project, @NotNull VcsLogCommitSelection selection) {
    MultiMap<Repo, CommitId> commitIds = groupCommits(project, ContainerUtil.getFirstItems(selection.getCommits(), MAX_SELECTED_COMMITS),
                                                      CommitId::getRoot);
    if (commitIds == null) return null;

    MultiMap<Repo, Hash> hashes = MultiMap.create();
    for (Map.Entry<Repo, Collection<CommitId>> entry: commitIds.entrySet()) {
      hashes.putValues(entry.getKey(), ContainerUtil.map(entry.getValue(), CommitId::getHash));
    }
    return hashes;
  }

  private @Nullable <T> MultiMap<Repo, T> groupCommits(@NotNull Project project,
                                                       @NotNull Collection<? extends T> commits,
                                                       @NotNull Function<? super T, ? extends VirtualFile> rootGetter) {
    MultiMap<Repo, T> map = MultiMap.create();
    for (T commit : commits) {
      Repo root = getRepositoryForRoot(project, rootGetter.fun(commit));
      if (root == null) { // commit from some other VCS
        return null;
      }
      map.putValue(root, commit);
    }
    return map;
  }
}
