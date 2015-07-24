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
package com.intellij.dvcs.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class VcsPushAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsRepositoryManager manager = VcsRepositoryManager.getInstance(project);
    Collection<Repository> repositories = e.getData(CommonDataKeys.EDITOR) != null
                                          ? ContainerUtil.<Repository>emptyList()
                                          : collectRepositories(manager, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
    VirtualFile selectedFile = DvcsUtil.getSelectedFile(project);
    new VcsPushDialog(project, DvcsUtil.sortRepositories(repositories),
                      selectedFile != null ? manager.getRepositoryForFile(selectedFile) : null).show();
  }

  @NotNull
  private static Collection<Repository> collectRepositories(@NotNull VcsRepositoryManager vcsRepositoryManager,
                                                            @Nullable VirtualFile[] files) {
    if (files == null) return Collections.emptyList();
    Collection<Repository> repositories = ContainerUtil.newHashSet();
    for (VirtualFile file : files) {
      Repository repo = vcsRepositoryManager.getRepositoryForFile(file);
      if (repo != null) {
        repositories.add(repo);
      }
    }
    return repositories;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    e.getPresentation()
      .setEnabledAndVisible(project != null && !VcsRepositoryManager.getInstance(project).getRepositories().isEmpty());
  }
}
