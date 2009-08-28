package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecentProjectsGroup extends ActionGroup {
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return RecentProjectsManagerBase.getInstance().getRecentProjectsActions(true);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(RecentProjectsManagerBase.getInstance().getRecentProjectsActions(true).length > 0);
  }
}
