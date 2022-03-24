// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareToggleAction;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ToggleShowTabLabelsAction extends DumbAwareToggleAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(
      !ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace())
      && e.getData(RunnerContentUi.KEY) != null
    );
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    var runnerUI = e.getData(RunnerContentUi.KEY);
    if (runnerUI == null) {
      return false;
    }
    return !runnerUI.getLayoutSettings().isTabLabelsHidden();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    var runnerUI = Objects.requireNonNull(e.getData(RunnerContentUi.KEY));
    runnerUI.getLayoutSettings().setTabLabelsHidden(!state);
    runnerUI.updateTabsUI(true);
  }
}
