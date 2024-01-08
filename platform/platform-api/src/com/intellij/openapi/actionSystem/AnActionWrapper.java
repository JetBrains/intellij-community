// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AnActionWrapper extends AnAction implements ActionWithDelegate<AnAction>, PerformWithDocumentsCommitted {
  private final AnAction myDelegate;

  public AnActionWrapper(@NotNull AnAction action) {
    myDelegate = action;
    copyFrom(action);
    setEnabledInModalContext(action.isEnabledInModalContext());
  }

  @Override
  public @NotNull AnAction getDelegate() {
    return myDelegate;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    UpdateSession session = e.getUpdateSession();
    JComponent customComponent =
      session == UpdateSession.EMPTY || !(this instanceof CustomComponentAction) ? null :
      e.getPresentation().getClientProperty(CustomComponentAction.COMPONENT_KEY);
    if (session == UpdateSession.EMPTY) myDelegate.update(e);
    else e.getPresentation().copyFrom(session.presentation(myDelegate), customComponent, true);
  }

  @Override
  public void beforeActionPerformedUpdate(@NotNull AnActionEvent e) {
    myDelegate.beforeActionPerformedUpdate(e);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myDelegate.actionPerformed(e);
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
  public boolean isPerformWithDocumentsCommitted() {
    return PerformWithDocumentsCommitted.isPerformWithDocumentsCommitted(myDelegate);
  }

  @Override
  public boolean isInInjectedContext() {
    return myDelegate.isInInjectedContext();
  }
}