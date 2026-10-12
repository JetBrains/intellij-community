// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class EditSourceAction extends BaseNavigateToSourceAction {
  public EditSourceAction() {
    super(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    if (e.getPlace().equals(ActionPlaces.ACTION_SEARCH)) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }
}
