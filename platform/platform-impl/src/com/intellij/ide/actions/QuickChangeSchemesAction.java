// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class QuickChangeSchemesAction extends QuickSwitchSchemeAction implements DumbAware {
  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    final AnAction[] actions = getGroup().getChildren(null);
    for (AnAction action : actions) {
      group.add(action);
    }
  }

  @Override
  protected String getPopupTitle(@NotNull AnActionEvent e) {
    return IdeBundle.message("popup.title.switch");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    super.actionPerformed(e);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("ui.scheme.quickswitch");
  }

  private static DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_CHANGE_SCHEME);
  }
}
