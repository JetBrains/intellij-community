// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * History must be provided via {@link History#KEY}.
 *
 * @see History
 */
abstract class NavigationAction extends AnAction implements DumbAware {
  protected NavigationAction(JComponent c, String originalActionID, @NotNull Disposable parentDisposable) {
    new ShadowAction(this, originalActionID, c,  parentDisposable);
    getTemplatePresentation().setIcon(ActionManager.getInstance().getAction(originalActionID).getTemplatePresentation().getIcon());
  }

  @Override
  public final void update(@NotNull final AnActionEvent e) {
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
