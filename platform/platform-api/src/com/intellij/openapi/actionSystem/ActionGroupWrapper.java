// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    UpdateSession session = e != null ? e.getUpdateSession() : UpdateSession.EMPTY;
    return session == UpdateSession.EMPTY ? myDelegate.getChildren(e) :
           session.children(myDelegate).toArray(AnAction.EMPTY_ARRAY);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    UpdateSession session = e.getUpdateSession();
    if (session == UpdateSession.EMPTY) myDelegate.update(e);
    else e.getPresentation().copyFrom(session.presentation(myDelegate), null, true);
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
  public boolean isPerformWithDocumentsCommitted() {
    return PerformWithDocumentsCommitted.isPerformWithDocumentsCommitted(myDelegate);
  }

  @Override
  public boolean isInInjectedContext() {
    return myDelegate.isInInjectedContext();
  }

  @Override
  public @NotNull List<AnAction> postProcessVisibleChildren(@NotNull List<? extends AnAction> visibleChildren,
                                                            @NotNull UpdateSession updateSession) {
    return myDelegate.postProcessVisibleChildren(visibleChildren, updateSession);
  }
}

