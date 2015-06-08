/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.data.LoadingDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class VcsLogSingleCommitAction<Repo extends Repository> extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);

    VcsFullCommitDetails details = ContainerUtil.getFirstItem(log.getSelectedDetails());
    assert details != null;
    Repo repository = getRepositoryForRoot(project, details.getRoot());
    assert repository != null;

    actionPerformed(repository, details);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    if (project == null || log == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    List<VcsFullCommitDetails> selectedDetails = log.getSelectedDetails();
    if (selectedDetails.isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      VcsFullCommitDetails details = ContainerUtil.getFirstItem(selectedDetails);
      if (details == null || details instanceof LoadingDetails) {
        e.getPresentation().setEnabledAndVisible(false);
      }
      else {
        Repo repository = getRepositoryForRoot(project, details.getRoot());

        if (repository == null) {
          e.getPresentation().setEnabledAndVisible(false);
        }
        else {
          e.getPresentation().setVisible(isVisible(project, repository, details));
          e.getPresentation().setEnabled(selectedDetails.size() == 1 && isEnabled(repository, details));
        }
      }
    }
  }

  protected abstract void actionPerformed(@NotNull Repo repository, @NotNull VcsFullCommitDetails commit);

  protected boolean isEnabled(@NotNull Repo repository, @NotNull VcsFullCommitDetails commit) {
    return true;
  }

  protected boolean isVisible(@NotNull final Project project, @NotNull Repo repository, @NotNull VcsFullCommitDetails commit) {
    return !getRepositoryManager(project).isExternal(repository);
  }

  @NotNull
  protected abstract AbstractRepositoryManager<Repo> getRepositoryManager(@NotNull Project project);

  @Nullable
  protected abstract Repo getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root);
}
