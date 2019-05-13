/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dvcs.ui;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
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
import static com.intellij.vcs.log.util.VcsLogUtil.collectFirstPack;

public abstract class VcsLogAction<Repo extends Repository> extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);

    log.requestSelectedDetails(details -> {
      MultiMap<Repo, VcsFullCommitDetails> grouped = groupCommits(project, details, VcsShortCommitDetails::getRoot);
      if (grouped == null) return;
      actionPerformed(project, grouped);
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    if (project == null || log == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    MultiMap<Repo, Hash> grouped = groupFirstPackOfCommits(project, log);
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

  @NotNull
  protected abstract AbstractRepositoryManager<Repo> getRepositoryManager(@NotNull Project project);

  @Nullable
  protected abstract Repo getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root);

  /**
   * Collects no more than VcsLogUtil.MAX_SELECTED_COMMITS and groups them by repository.
   * To use only during update.
   */
  @Nullable
  private MultiMap<Repo, Hash> groupFirstPackOfCommits(@NotNull Project project, @NotNull VcsLog log) {
    MultiMap<Repo, CommitId> commitIds = groupCommits(project, collectFirstPack(log.getSelectedCommits(), MAX_SELECTED_COMMITS),
                                                      CommitId::getRoot);
    if (commitIds == null) return null;

    MultiMap<Repo, Hash> hashes = MultiMap.create();
    for (Map.Entry<Repo, Collection<CommitId>> entry: commitIds.entrySet()) {
      hashes.putValues(entry.getKey(), ContainerUtil.map(entry.getValue(), CommitId::getHash));
    }
    return hashes;
  }

  @Nullable
  private <T> MultiMap<Repo, T> groupCommits(@NotNull Project project,
                                             @NotNull Collection<T> commits,
                                             @NotNull Function<T, VirtualFile> rootGetter) {
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
