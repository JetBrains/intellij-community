// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ActionGroupWrapper extends ActionGroup implements ActionWithDelegate<ActionGroup>, PerformWithDocumentsCommitted {
  private final ActionGroup myDelegate;

  public ActionGroupWrapper(@NotNull ActionGroup action) {
    myDelegate = action;
    copyFrom(action);
    setEnabledInModalContext(action.isEnabledInModalContext());
  }

  @Override
  public final @NotNull ActionGroup getDelegate() {
    return myDelegate;
  }

  @Override
  public boolean isDumbAware() {
    return myDelegate.isDumbAware();
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return myDelegate.getActionUpdateThread();
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return myDelegate.getChildren(e);
  }

  @Override
  public final boolean isPopup() {
    return myDelegate.isPopup();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    myDelegate.update(e);
  }

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    myDelegate.beforeActionPerformedUpdate(e);
  }

  @Override
  public boolean canBePerformed(@NotNull DataContext context) {
    return myDelegate.canBePerformed(context);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myDelegate.actionPerformed(e);
  }

  @Override
  public boolean isPerformWithDocumentsCommitted() {
    return PerformWithDocumentsCommitted.isPerformWithDocumentsCommitted(myDelegate);
  }

  @Override
  public boolean isInInjectedContext() {
    return myDelegate.isInInjectedContext();
  }

  @Override
  public boolean hideIfNoVisibleChildren() {
    return myDelegate.hideIfNoVisibleChildren();
  }

  @Override
  public boolean disableIfNoVisibleChildren() {
    return myDelegate.disableIfNoVisibleChildren();
  }
}

