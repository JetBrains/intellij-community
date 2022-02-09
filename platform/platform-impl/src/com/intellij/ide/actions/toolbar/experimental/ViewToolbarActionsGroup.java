// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.toolbar.experimental;

import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class ViewToolbarActionsGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isEnabled = !ToolbarSettings.getInstance().isEnabled();
    e.getPresentation().setEnabledAndVisible(isEnabled);
  }
}
