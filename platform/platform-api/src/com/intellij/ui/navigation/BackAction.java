// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.navigation;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class BackAction extends NavigationAction {
  public BackAction(JComponent c, @NotNull Disposable parentDisposable) {
    super(c, "Back", parentDisposable);
  }

  @Override
  protected void doUpdate(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e).canGoBack());
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    getHistory(e).back();
  }
}
