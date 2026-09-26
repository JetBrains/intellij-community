// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.RecentChangesPopup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.platform.lvcs.impl.ActivityScope;
import com.intellij.platform.lvcs.impl.statistics.LocalHistoryCounter;
import com.intellij.platform.lvcs.impl.ui.ActivityView;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ApiStatus.Internal
public final class RecentChangesAction extends LocalHistoryAction {
  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    if (ActivityView.isViewEnabled()) {
      ActivityView.showInToolWindow(p, gw, ActivityScope.Recent.INSTANCE);
    }
    else {
      LocalHistoryCounter.INSTANCE.logLocalHistoryOpened(LocalHistoryCounter.Kind.Recent);
      RecentChangesPopup.show(p, gw, Objects.requireNonNull(getVcs()));
    }
  }
}