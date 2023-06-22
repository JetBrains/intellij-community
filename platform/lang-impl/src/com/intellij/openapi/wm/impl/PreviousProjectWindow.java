// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.IdeDependentAction;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

final class PreviousProjectWindow extends IdeDependentAction implements DumbAware, LightEditCompatible {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    WindowDressing.getWindowActionGroup().activatePreviousWindow(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(WindowDressing.getWindowActionGroup().isEnabled());
    super.update(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
