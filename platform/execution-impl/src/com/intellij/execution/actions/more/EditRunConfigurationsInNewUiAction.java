// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions.more;

import com.intellij.execution.actions.EditRunConfigurationsAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

final class EditRunConfigurationsInNewUiAction extends EditRunConfigurationsAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!ExperimentalUI.isNewUI()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    super.update(e);
  }
}
