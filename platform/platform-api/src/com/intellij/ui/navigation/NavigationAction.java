// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.navigation;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.ShadowAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class NavigationAction extends AnAction implements DumbAware {
  protected NavigationAction(JComponent c, final String originalActionID, @NotNull Disposable parentDisposable) {
    final AnAction original = ActionManager.getInstance().getAction(originalActionID);
    new ShadowAction(this, original, c, parentDisposable);
    getTemplatePresentation().setIcon(original.getTemplatePresentation().getIcon());
  }

  @Override
  public final void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e) != null);
    if (e.getPresentation().isEnabled()) {
      doUpdate(e);
    }
  }

  protected abstract void doUpdate(final AnActionEvent e);

  @Nullable
  protected static History getHistory(final AnActionEvent e) {
    return e.getData(History.KEY);
  }
}
