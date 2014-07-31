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
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class VcsLogSingleCommitAction<Repo extends Repository> extends DumbAwareAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VSC_LOG);
    List<VcsFullCommitDetails> details = log.getSelectedDetails();
    assert details.size() == 1;
    VcsFullCommitDetails commit = details.get(0);
    Repo repository = getRepositoryForRoot(project, commit.getRoot());
    assert repository != null;

    actionPerformed(repository, commit);
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
    if (containsCommitFromOtherVcs(project, details)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(details.size() == 1);
    }
  }

  @Nullable
  protected abstract Repo getRepositoryForRoot(@NotNull Project project, @NotNull VirtualFile root);

  protected abstract void actionPerformed(@NotNull Repo repository, @NotNull VcsFullCommitDetails commit);

  private boolean containsCommitFromOtherVcs(@NotNull final Project project, @NotNull List<VcsFullCommitDetails> details) {
    return ContainerUtil.exists(details, new Condition<VcsFullCommitDetails>() {
      @Override
      public boolean value(VcsFullCommitDetails details) {
        return getRepositoryForRoot(project, details.getRoot()) == null;
      }
    });
  }

}
