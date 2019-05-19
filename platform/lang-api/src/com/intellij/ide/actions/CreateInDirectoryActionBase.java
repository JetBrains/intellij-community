// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * The base abstract class for actions which create new file elements in IDE view
 */
public abstract class CreateInDirectoryActionBase extends AnAction {
  protected CreateInDirectoryActionBase() {
  }

  protected CreateInDirectoryActionBase(@Nls(capitalization = Nls.Capitalization.Title) String text,
                                        @Nls(capitalization = Nls.Capitalization.Sentence) String description,
                                        Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    boolean enabled = isAvailable(e);

    e.getPresentation().setEnabledAndVisible(enabled);
  }


  @Override
  public boolean startInTransaction() {
    return true;
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
