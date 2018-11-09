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
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The element of the branch popup which allows to show branches of the selected repository.
 * It is available only in projects with multiple roots.
 */
public class RootAction<T extends Repository> extends ActionGroup implements PopupElementWithAdditionalInfo, DumbAware {

  @NotNull protected final T myRepository;
  @NotNull private final ActionGroup myGroup;
  @Nullable private final String myBranchText;

  public RootAction(@NotNull T repository, @NotNull ActionGroup actionsGroup, @Nullable String branchText) {
    super("", true);
    myRepository = repository;
    myGroup = actionsGroup;
    myBranchText = branchText;
    getTemplatePresentation().setText(DvcsUtil.getShortRepositoryName(repository), false);
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myGroup.getChildren(e);
  }

  @Nullable
  @Override
  public String getInfoText() {
    return myBranchText;
  }
}




