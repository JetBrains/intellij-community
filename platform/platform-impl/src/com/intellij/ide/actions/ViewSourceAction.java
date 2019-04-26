
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;


import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import org.jetbrains.annotations.NotNull;

public class ViewSourceAction extends BaseNavigateToSourceAction {
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
