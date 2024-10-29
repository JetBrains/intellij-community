// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ShowSearchHistoryAction extends AnAction implements ActionRemoteBehaviorSpecification.Frontend {
  ShowSearchHistoryAction() {
    super(IdeBundle.messagePointer("action.AnAction.text.search.history"),
          IdeBundle.messagePointer("action.AnAction.description.search.history"), AllIcons.Actions.SearchWithHistory);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    //do nothing, it's just shortcut-holding action
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(false);
  }
}
