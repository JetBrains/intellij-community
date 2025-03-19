// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class ActionWrapperUtil {
  private ActionWrapperUtil() {
  }

  public static void update(@NotNull AnActionEvent e,
                            @NotNull AnAction wrapper,
                            @NotNull AnAction delegate) {
    UpdateSession session = e.getUpdateSession();
    JComponent customComponent = e.getPresentation().getClientProperty(CustomComponentAction.COMPONENT_KEY);
    AnActionEvent event = customizeEvent(e, wrapper);
    if (session == UpdateSession.EMPTY || e != event) delegate.update(event);
    else e.getPresentation().copyFrom(session.presentation(delegate), customComponent, true);

    if (delegate instanceof CustomComponentAction o && !(wrapper instanceof CustomComponentAction) &&
        e.getPresentation().getClientProperty(ActionUtil.COMPONENT_PROVIDER) == null) {
      e.getPresentation().putClientProperty(ActionUtil.COMPONENT_PROVIDER, o);
    }
  }

  public static AnAction @NotNull [] getChildren(@Nullable AnActionEvent e,
                                                 @NotNull ActionGroup wrapper,
                                                 @NotNull ActionGroup delegate) {
    if (e == null) return delegate.getChildren(null);
    UpdateSession session = e.getUpdateSession();
    AnActionEvent event = customizeEvent(e, wrapper);
    return session == UpdateSession.EMPTY || e != event ? delegate.getChildren(event) :
           session.children(delegate).toArray(AnAction.EMPTY_ARRAY);
  }

  public static void actionPerformed(@NotNull AnActionEvent e,
                                     @NotNull AnAction wrapper,
                                     @NotNull AnAction delegate) {
    delegate.actionPerformed(customizeEvent(e, wrapper));
  }

  public static @NotNull ActionUpdateThread getActionUpdateThread(@NotNull AnAction wrapper,
                                                                  @NotNull AnAction delegate) {
    if (wrapper instanceof DataSnapshotProvider) return delegate.getActionUpdateThread();
    return ActionUpdateThread.BGT;
  }

  private static @NotNull AnActionEvent customizeEvent(@NotNull AnActionEvent e,
                                                       @NotNull AnAction wrapper) {
    return wrapper instanceof DataSnapshotProvider o ?
           e.withDataContext(CustomizedDataContext.withSnapshot(e.getDataContext(), o)) : e;
  }
}
