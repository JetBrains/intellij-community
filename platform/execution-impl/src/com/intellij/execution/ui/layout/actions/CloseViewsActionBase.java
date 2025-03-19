// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public abstract class CloseViewsActionBase extends BaseViewAction implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  protected void update(AnActionEvent e, ViewContext context, Content[] content) {
    e.getPresentation().setEnabledAndVisible(isEnabled(context, content, e.getPlace()));
  }

  @Override
  protected void actionPerformed(AnActionEvent e, ViewContext context, Content[] content) {
    ContentManager manager = context.getContentManager();
    for (Content c : manager.getContents()) {
      if (c.isCloseable() && isAccepted(c, content)) {
        manager.removeContent(c, context.isToDisposeRemovedContent());
      }
    }
  }

  public boolean isEnabled(ViewContext context, Content[] selectedContents, String place) {
    for (Content c : context.getContentManager().getContents()) {
      if (c.isCloseable() && isAccepted(c, selectedContents)) return true;
    }
    return false;
  }

  protected abstract boolean isAccepted(@NotNull Content c, Content @NotNull [] selectedContents);
}