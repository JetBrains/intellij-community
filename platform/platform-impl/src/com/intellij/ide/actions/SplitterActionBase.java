// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class SplitterActionBase extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull final AnActionEvent event) {
    final Project project = event.getProject();
    final Presentation presentation = event.getPresentation();
    boolean inContextMenu = ActionPlaces.isPopupPlace(event.getPlace());
    boolean enabled = project != null && isActionEnabled(project, inContextMenu);
    if (inContextMenu) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setEnabled(enabled);
    }
  }

  /**
   * This method determines whether the action is enabled for {@code project}
   * if {@code inContextMenu} is set to {@code false}.  Otherwise,
   * it determines whether the action is visible in the context menu.
   *
   * @param project       the specified project
   * @param inContextMenu whether the action is used in context menu
   * @return              {@code true} if the action is enabled,
   *                      {@code false} otherwise
   */
  protected boolean isActionEnabled(Project project, boolean inContextMenu) {
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    return fileEditorManager.isInSplitter();
  }
}
