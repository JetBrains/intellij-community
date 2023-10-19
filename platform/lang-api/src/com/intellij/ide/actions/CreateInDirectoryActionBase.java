// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * The base abstract class for actions which create new file elements in IDE view
 */
public abstract class CreateInDirectoryActionBase extends AnAction {
  protected CreateInDirectoryActionBase() {
  }

  protected CreateInDirectoryActionBase(@NlsActions.ActionText String text,
                                        @NlsActions.ActionDescription String description,
                                        Icon icon) {
    super(text, description, icon);
  }

  protected CreateInDirectoryActionBase(@NotNull Supplier<@NlsActions.ActionText String> dynamicText,
                                        @NotNull Supplier<@NlsActions.ActionDescription String> dynamicDescription,
                                        Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  protected CreateInDirectoryActionBase(@NotNull Supplier<@NlsActions.ActionText String> dynamicText,
                                        @Nullable Supplier<@NlsActions.ActionDescription String> dynamicDescription,
                                        @Nullable Supplier<? extends @Nullable Icon> icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    boolean enabled = isAvailable(e);

    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public boolean isDumbAware() {
    return false;
  }

  protected boolean isAvailable(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    return isAvailable(dataContext);
  }

  protected boolean isAvailable(final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    if (DumbService.getInstance(project).isDumb() && !isDumbAware()) {
      return false;
    }

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null || view.getDirectories().length == 0) {
      return false;
    }

    return true;
  }
}
