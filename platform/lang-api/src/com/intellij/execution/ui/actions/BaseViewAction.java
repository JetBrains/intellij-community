// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.ui.actions;

import com.intellij.execution.ui.layout.Grid;
import com.intellij.execution.ui.layout.Tab;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseViewAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void update(final @NotNull AnActionEvent e) {
    ViewContext context = getViewFacade(e);
    Content[] content = getContent(e);

    if (context != null && content != null && !containsInvalidContent(content)) {
      update(e, context, content);
    }
    else {
      setEnabled(e, false);
    }
  }

  private static boolean containsInvalidContent(Content[] content) {
    for (Content each : content) {
      if (!each.isValid()) {
        return true;
      }
    }

    return false;
  }

  protected void update(@NotNull AnActionEvent e, @NotNull ViewContext context, Content @NotNull [] content) {
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    ViewContext context = getViewFacade(e);
    Content[] content = getContent(e);
    if (context != null && content != null && !containsInvalidContent(content)) {
      actionPerformed(e, context, content);
    }
  }


  protected abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull ViewContext context, Content @NotNull [] content);


  private static @Nullable ViewContext getViewFacade(@NotNull AnActionEvent e) {
    return e.getData(ViewContext.CONTEXT_KEY);
  }

  private static Content @Nullable [] getContent(@NotNull AnActionEvent e) {
    return e.getData(ViewContext.CONTENT_KEY);
  }

  protected static @Nullable Tab getTabFor(final ViewContext context, final Content[] content) {
    Grid grid = context.findGridFor(content[0]);
    return context.getTabFor(grid);
  }

  protected static void setEnabled(AnActionEvent e, boolean enabled) {
    e.getPresentation().setVisible(enabled);
  }
}
