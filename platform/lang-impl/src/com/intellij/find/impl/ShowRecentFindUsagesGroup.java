// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.usages.ConfigurableUsageTarget;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ShowRecentFindUsagesGroup extends ActionGroup {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setPerformGroup(!e.isFromActionToolbar());
    e.getPresentation().setDisableGroupIfEmpty(false);
    e.getPresentation().putClientProperty(ActionMenu.SUPPRESS_SUBMENU, true);
    e.getPresentation().setEnabledAndVisible(project != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    JBPopupFactory.getInstance().createActionGroupPopup(
      e.getPresentation().getText(), this, e.getDataContext(),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      false, null, -1, null, ActionPlaces.getActionGroupPopupPlace(e.getPlace()))
      .showInBestPositionFor(e.getDataContext());
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || DumbService.isDumb(project)) return EMPTY_ARRAY;
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    List<ConfigurableUsageTarget> history = new ArrayList<>(findUsagesManager.getHistory().getAll());
    Collections.reverse(history);

    String description = ActionsBundle.actionDescription(UsageViewImpl.SHOW_RECENT_FIND_USAGES_ACTION_ID);

    List<AnAction> children = new ArrayList<>(history.size());
    for (ConfigurableUsageTarget usageTarget : history) {
      if (!usageTarget.isValid()) {
        continue;
      }
      String text = usageTarget.getLongDescriptiveName();
      AnAction action = new AnAction(text, description, null) {
        @Override
        public void actionPerformed(final @NotNull AnActionEvent e) {
          findUsagesManager.rerunAndRecallFromHistory(usageTarget);
        }
      };
      children.add(action);
    }
    return children.toArray(AnAction.EMPTY_ARRAY);
  }
}
