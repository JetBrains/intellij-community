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

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class VcsLogAction<Repo extends Repository> extends DumbAwareAction {

  protected enum Mode {
    SINGLE_COMMIT,
    SINGLE_PER_REPO
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VSC_LOG);
    List<VcsFullCommitDetails> details = log.getSelectedDetails();
    MultiMap<Repo, VcsFullCommitDetails> grouped = groupByRoot(project, details);
    assert grouped != null;
    Map<Repo, VcsFullCommitDetails> singleElementMap = convertToSingleElementMap(grouped);
    assert singleElementMap != null;
    actionPerformed(project, singleElementMap);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    VcsLog log = e.getData(VcsLogDataKeys.VSC_LOG);
    if (project == null || log == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    List<VcsFullCommitDetails> details = log.getSelectedDetails();
    MultiMap<Repo, VcsFullCommitDetails> grouped = groupByRoot(project, details);
    if (grouped == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(isEnabled(grouped));
    }
  }

  private boolean isEnabled(@NotNull MultiMap<Repo, VcsFullCommitDetails> grouped) {
    if (grouped.isEmpty()) {
      return false;
    }
    switch (getMode()) {
      case SINGLE_COMMIT:
        return grouped.size() == 1;
      case SINGLE_PER_REPO:
        return allValuesAreSingletons(grouped);
      default:
        return false;
    }
  }

  @Nullable
  protected abstract Repo getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root);

  protected abstract void actionPerformed(@NotNull Project project, @NotNull Map<Repo, VcsFullCommitDetails> commits);

  @NotNull
  protected abstract Mode getMode();

  @Nullable
  private MultiMap<Repo, VcsFullCommitDetails> groupByRoot(@NotNull Project project, @NotNull List<VcsFullCommitDetails> commits) {
    MultiMap<Repo, VcsFullCommitDetails> map = MultiMap.create();
    for (VcsFullCommitDetails commit : commits) {
      Repo root = getRepositoryForRoot(project, commit.getRoot());
      if (root == null) { // commit from some other VCS
        return null;
      }
      map.putValue(root, commit);
    }
    return map;
  }

  private boolean allValuesAreSingletons(@NotNull MultiMap<Repo, VcsFullCommitDetails> grouped) {
    return !ContainerUtil.exists(grouped.entrySet(), new Condition<Map.Entry<Repo, Collection<VcsFullCommitDetails>>>() {
      @Override
      public boolean value(Map.Entry<Repo, Collection<VcsFullCommitDetails>> entry) {
        return entry.getValue().size() != 1;
      }
    });
  }

  @Nullable
  private Map<Repo, VcsFullCommitDetails> convertToSingleElementMap(@NotNull MultiMap<Repo, VcsFullCommitDetails> groupedCommits) {
    Map<Repo, VcsFullCommitDetails> map = ContainerUtil.newHashMap();
    for (Map.Entry<Repo, Collection<VcsFullCommitDetails>> entry : groupedCommits.entrySet()) {
      Collection<VcsFullCommitDetails> commits = entry.getValue();
      if (commits.size() != 1) {
        return null;
      }
      map.put(entry.getKey(), commits.iterator().next());
    }
    return map;
  }

}
