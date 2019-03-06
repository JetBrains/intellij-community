// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class MockActionDeleteMe extends AnAction {

  public MockActionDeleteMe() {
    super();
    getTemplatePresentation().setEnabledAndVisible(false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {

  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
