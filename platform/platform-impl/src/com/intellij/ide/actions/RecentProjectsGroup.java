// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class RecentProjectsGroup extends ActionGroup implements DumbAware {
  public RecentProjectsGroup() {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(ActionsBundle.message(SystemInfo.isMac ? "group.reopen.mac.text": "group.reopen.win.text"));
  }

  @Override
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return removeCurrentProject(e == null ? null : e.getProject(), RecentProjectsManager.getInstance().getRecentProjectsActions(true));
  }

  public static AnAction[] removeCurrentProject(Project project, AnAction[] actions) {
    if (project != null) {
      return Arrays.stream(actions)
                   .filter(action -> !(action instanceof ReopenProjectAction)
                                     || !StringUtil.equals(((ReopenProjectAction)action).getProjectPath(), project.getBasePath()))
                   .toArray(AnAction[]::new);
    }
    return actions;
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(RecentProjectsManager.getInstance().getRecentProjectsActions(true).length > 0);
  }
}
