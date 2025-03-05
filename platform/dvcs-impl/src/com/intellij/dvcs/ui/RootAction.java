// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The element of the branch popup which allows to show branches of the selected repository.
 * It is available only in projects with multiple roots.
 */
public class RootAction<T extends Repository> extends ActionGroup
  implements PopupElementWithAdditionalInfo, DumbAware, ActionUpdateThreadAware.Recursive {

  protected final @NotNull T myRepository;
  private final @NotNull ActionGroup myGroup;
  private final @Nullable @Nls String myBranchText;

  public RootAction(@NotNull T repository, @NotNull ActionGroup actionsGroup, @Nullable @Nls String branchText) {
    super("", true);
    myRepository = repository;
    myGroup = actionsGroup;
    myBranchText = branchText;
    getTemplatePresentation().setText(DvcsUtil.getShortRepositoryName(repository), false);
    getTemplatePresentation().putClientProperty(ActionUtil.ALWAYS_VISIBLE_GROUP, true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return myGroup.getChildren(e);
  }

  @Override
  public @Nullable String getInfoText() {
    return myBranchText;
  }
}




