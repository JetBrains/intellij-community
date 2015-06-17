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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.impl.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public abstract class VcsLogAction<Repo extends Repository> extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);

    log.requestSelectedDetails(new Consumer<Set<VcsFullCommitDetails>>() {
      @Override
      public void consume(Set<VcsFullCommitDetails> details) {
        MultiMap<Repo, VcsFullCommitDetails> grouped = groupCommits(project, details, false);
        if (grouped == null) return;
        actionPerformed(project, grouped);
      }
    }, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    if (project == null || log == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    MultiMap<Repo, VcsFullCommitDetails> grouped = getGroupedCommits(project, log, true);
    if (grouped == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setVisible(isVisible(project, grouped));
      e.getPresentation().setEnabled(!grouped.isEmpty() && isEnabled(grouped));
    }
  }

  protected abstract void actionPerformed(@NotNull Project project, @NotNull MultiMap<Repo, VcsFullCommitDetails> grouped);

  protected abstract boolean isEnabled(@NotNull MultiMap<Repo, VcsFullCommitDetails> grouped);

  protected boolean isVisible(@NotNull final Project project, @NotNull MultiMap<Repo, VcsFullCommitDetails> grouped) {
    return ContainerUtil.and(grouped.keySet(), new Condition<Repo>() {
      @Override
      public boolean value(Repo repo) {
        RepositoryManager<Repo> manager = getRepositoryManager(project);
        return !manager.isExternal(repo);
      }
    });
  }

  @NotNull
  protected abstract AbstractRepositoryManager<Repo> getRepositoryManager(@NotNull Project project);

  @Nullable
  protected abstract Repo getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root);

  @Nullable
  private MultiMap<Repo, VcsFullCommitDetails> getGroupedCommits(@NotNull Project project, @NotNull VcsLog log, boolean fromUpdate) {
    return groupCommits(project, VcsLogUtil.collectLoadedSelectedDetails(log, fromUpdate), fromUpdate);
  }

  @Nullable
  private MultiMap<Repo, VcsFullCommitDetails> groupCommits(@NotNull Project project,
                                                            Collection<VcsFullCommitDetails> commits,
                                                            boolean fromUpdate) {
    MultiMap<Repo, VcsFullCommitDetails> map = MultiMap.create();
    for (VcsFullCommitDetails commit : commits) {
      Repo root = getRepositoryForRoot(project, commit.getRoot());
      if (root == null) { // commit from some other VCS
        if (!fromUpdate) {
          VcsNotifier.getInstance(project).notifyWeakError(
            "Can not perform action on commit " + commit.getId().toShortString() + " from root " + commit.getRoot().getName());
        }
        return null;
      }
      map.putValue(root, commit);
    }
    return map;
  }

}
