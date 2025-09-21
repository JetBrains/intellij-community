// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RecentProjectsGroup extends ActionGroup implements DumbAware, ActionRemoteBehaviorSpecification.BackendOnly {

  public RecentProjectsGroup() {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(ActionsBundle.messagePointer(SystemInfo.isMac ? "group.reopen.mac.text" : "group.reopen.win.text"));
  }


  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return removeCurrentProject(e == null ? null : e.getProject(), RecentProjectListActionProvider.getInstance().getActions(true));
  }

  public static AnAction[] removeCurrentProject(Project project, @NotNull List<? extends AnAction> actions) {
    if (project == null) {
      return actions.toArray(EMPTY_ARRAY);
    }

    RecentProjectListActionProvider provider = RecentProjectListActionProvider.getInstance();
    List<AnAction> list = new ArrayList<>();
    for (AnAction action : actions) {
      if (!(action instanceof ReopenProjectAction) || !provider.isCurrentProjectAction(project, (ReopenProjectAction)action)) {
        list.add(action);
      }
    }
    return list.toArray(EMPTY_ARRAY);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(!RecentProjectListActionProvider.getInstance().getActions(true).isEmpty());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
