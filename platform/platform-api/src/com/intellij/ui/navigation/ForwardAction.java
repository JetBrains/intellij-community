// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.navigation;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ForwardAction extends NavigationAction {

  public ForwardAction(JComponent c, @NotNull Disposable parentDisposable) {
    super(c, "Forward", parentDisposable);
  }

  @Override
  protected void doUpdate(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e).canGoForward());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    getHistory(e).forward();
  }
}