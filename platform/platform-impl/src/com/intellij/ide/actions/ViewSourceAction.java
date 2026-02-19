
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;


import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ViewSourceAction extends BaseNavigateToSourceAction {
  public ViewSourceAction() {
    super(false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getData(CommonDataKeys.EDITOR) != null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      super.update(e);
    }
  }
}
