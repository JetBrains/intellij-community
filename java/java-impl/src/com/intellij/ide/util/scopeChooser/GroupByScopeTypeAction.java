// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.scopeChooser;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.packageDependencies.DependencyUISettings;
import com.intellij.packageDependencies.ui.ProjectPatternProvider;
import org.jetbrains.annotations.NotNull;

public final class GroupByScopeTypeAction extends ToggleAction {
  private final Runnable myUpdate;

  public GroupByScopeTypeAction(Runnable update) {
    super(IdeBundle.message("action.group.by.scope.type"),
          IdeBundle.message("action.description.group.by.scope"), AllIcons.Actions.GroupByTestProduction);
    myUpdate = update;
  }

  @Override
  public boolean isSelected(AnActionEvent event) {
    return DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE;
  }

  @Override
  public void setSelected(AnActionEvent event, boolean flag) {
    DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
    myUpdate.run();
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(!ProjectPatternProvider.FILE.equals(DependencyUISettings.getInstance().SCOPE_TYPE));
  }
}
