// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.RecentChangesPopup;
import com.intellij.platform.lvcs.ActivityScope;
import com.intellij.platform.lvcs.ui.ActivityView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class RecentChangesAction extends LocalHistoryAction {
  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    if (ActivityView.isViewEnabled()) {
      ActivityView.show(p, gw, ActivityScope.Recent.INSTANCE);
    }
    else {
      RecentChangesPopup.show(p, gw, Objects.requireNonNull(getVcs()));
    }
  }
}