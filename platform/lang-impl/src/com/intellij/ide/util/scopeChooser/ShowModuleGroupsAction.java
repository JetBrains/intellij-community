// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util.scopeChooser;

import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.packageDependencies.DependencyUISettings;
import org.jetbrains.annotations.NotNull;

public final class ShowModuleGroupsAction extends ToggleAction {
  private final Runnable myUpdate;

  public ShowModuleGroupsAction(final Runnable update) {
    super(LangBundle.message("action.ShowModuleGroupsAction.show.module.groups.text"),
          LangBundle.message("action.ShowModuleGroupsAction.show.hide.module.groups.description"), AllIcons.Actions.GroupByModuleGroup);
    myUpdate = update;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent event) {
    return DependencyUISettings.getInstance().UI_SHOW_MODULE_GROUPS;
  }
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent event, boolean flag) {
    DependencyUISettings.getInstance().UI_SHOW_MODULE_GROUPS = flag;
    myUpdate.run();
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(DependencyUISettings.getInstance().UI_SHOW_MODULES);
  }
}
