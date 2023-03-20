// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class NewWatchAction
 * @author Jeka
 */
package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RestoreLayoutAction extends DumbAwareAction {
  
  public static @Nullable RunnerContentUi getRunnerUi(@NotNull AnActionEvent e) {
    return e.getData(RunnerContentUi.KEY);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    RunnerContentUi ui = getRunnerUi(e);
    if (ui != null) {
      ui.restoreLayout();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    RunnerContentUi runnerContentUi = getRunnerUi(e);
    boolean enabled = false;
    if (runnerContentUi != null) {
      enabled = true;
      if (ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace())) {
        // In this case the action has to available in ActionPlaces.RUNNER_LAYOUT_BUTTON_TOOLBAR only
        enabled = !runnerContentUi.isMinimizeActionEnabled();
      }
    }
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}