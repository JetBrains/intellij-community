// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.usages.ConfigurableUsageTarget;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public final class ShowRecentFindUsagesAction extends AnAction implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(e.getData(UsageView.USAGE_VIEW_KEY) != null &&
                                   project != null &&
                                   ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager().getHistory().getAll().size() >
                                   1);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    UsageView usageView = e.getData(UsageView.USAGE_VIEW_KEY);
    Project project = Objects.requireNonNull(e.getProject());
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    Map<ConfigurableUsageTarget, String> historyData = findUsagesManager.getHistory().getAllHistoryData();
    List<ConfigurableUsageTarget> history = new ArrayList<>(historyData.keySet());

    if (!history.isEmpty()) {
      // skip most recent find usage, it's under your nose
      history.remove(history.size() - 1);
      Collections.reverse(history);
    }
    if (history.isEmpty()) {
      history.add(null); // to fill the popup
    }

    BaseListPopupStep<ConfigurableUsageTarget> step =
      new BaseListPopupStep<>(FindBundle.message("recent.find.usages.action.title"), history) {
        @Override
        public Icon getIconFor(final ConfigurableUsageTarget data) {
          return null;
        }

        @Override
        public @NotNull String getTextFor(final ConfigurableUsageTarget data) {
          if (data == null) {
            return FindBundle.message("recent.find.usages.action.nothing");
          }
          return historyData.get(data);
        }

        @Override
        public PopupStep<?> onChosen(final ConfigurableUsageTarget selectedValue, final boolean finalChoice) {
          return doFinalStep(() -> {
            if (selectedValue != null) {
              findUsagesManager.rerunAndRecallFromHistory(selectedValue);
            }
          });
        }
      };
    JBPopupFactory.getInstance().createListPopup(step).showInCenterOf(usageView.getPreferredFocusableComponent());
  }
}
