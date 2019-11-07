/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public abstract class NewBranchAction<T extends Repository> extends DumbAwareAction {
  public static final String text = "New Branch";
  public static final String description = "Create and checkout new branch";
  public static final Icon icon = AllIcons.General.Add;

  protected final List<T> myRepositories;
  protected final Project myProject;

  public NewBranchAction(@NotNull Project project, @NotNull List<T> repositories) {
    super(text, description, icon);
    myRepositories = repositories;
    myProject = project;
  }


  @Override
  public void update(@NotNull AnActionEvent e) {
    checkIfAnyRepositoryIsFresh(e, myRepositories);
  }

  public static <T extends Repository> void checkIfAnyRepositoryIsFresh(@NotNull AnActionEvent e, @NotNull List<T> repositories) {
    if (DvcsUtil.anyRepositoryIsFresh(repositories)) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setDescription("Checkout of a new branch is not possible before the first commit");
    }
  }

  @Override
  public abstract void actionPerformed(@NotNull AnActionEvent e);
}
