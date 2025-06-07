// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class ActionGroupWrapper extends ActionGroup implements ActionWithDelegate<ActionGroup>, PerformWithDocumentsCommitted {
  private final ActionGroup myDelegate;

  public ActionGroupWrapper(@NotNull ActionGroup action) {
    myDelegate = action;
    copyFrom(action);
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
    return ActionWrapperUtil.getActionUpdateThread(this, myDelegate);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return ActionWrapperUtil.getChildren(e, this, myDelegate);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ActionWrapperUtil.update(e, this, myDelegate);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ActionWrapperUtil.actionPerformed(e, this, myDelegate);
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
  public @Unmodifiable @NotNull List<? extends @NotNull AnAction> postProcessVisibleChildren(@NotNull AnActionEvent e,
                                                                                             @NotNull List<? extends AnAction> visibleChildren) {
    return myDelegate.postProcessVisibleChildren(e, visibleChildren);
  }
}

