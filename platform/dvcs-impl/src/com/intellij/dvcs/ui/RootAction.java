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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The element of the branch popup which allows to show branches of the selected repository.
 * It is available only in projects with multiple roots.
 *
 * @author Kirill Likhodedov
 * @author Nadya Zabrodina
 */
public class RootAction<T extends Repository> extends ActionGroup {

  @NotNull protected final T myRepository;
  @NotNull private final ActionGroup myGroup;
  @NotNull private final String myBranchText;

  /**
   * @param currentRepository Pass null in the case of common repositories - none repository will be highlighted then.
   * @param actionsGroup
   * @param branchText
   */
  public RootAction(@NotNull T repository, @Nullable T currentRepository, @NotNull ActionGroup actionsGroup, @NotNull String branchText) {
    super("", true);
    myRepository = repository;
    myGroup = actionsGroup;
    myBranchText = branchText;
    if (repository.equals(currentRepository)) {
      getTemplatePresentation().setIcon(PlatformIcons.CHECK_ICON);
    }
    getTemplatePresentation().setText(DvcsUtil.getShortRepositoryName(repository), false);
  }

  @NotNull
  public String getCaption() {
    return "Current branch in " + DvcsUtil.getShortRepositoryName(myRepository) + ": " + getDisplayableBranchText();
  }

  @NotNull
  public String getDisplayableBranchText() {
    return myBranchText;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myGroup.getChildren(e);
  }
}




