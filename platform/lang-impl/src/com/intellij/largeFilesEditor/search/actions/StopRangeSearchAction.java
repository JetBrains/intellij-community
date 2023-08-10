// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search.actions;

import com.intellij.icons.AllIcons;
import com.intellij.largeFilesEditor.search.searchResultsPanel.RangeSearch;
import com.intellij.largeFilesEditor.search.searchTask.RangeSearchTask;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class StopRangeSearchAction extends AnAction implements DumbAware {

  private final RangeSearch myRangeSearch;

  public StopRangeSearchAction(@NotNull RangeSearch rangeSearch) {
    this.myRangeSearch = rangeSearch;
    getTemplatePresentation().setText(EditorBundle.messagePointer("large.file.editor.stop.searching.action.text"));
    getTemplatePresentation().setIcon(AllIcons.Actions.Suspend);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    RangeSearchTask task = myRangeSearch.getLastExecutedRangeSearchTask();
    e.getPresentation().setEnabled(
      task != null && !task.isFinished() && !task.isShouldStop()
    );
  }
  
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RangeSearchTask task = myRangeSearch.getLastExecutedRangeSearchTask();
    if (task != null) {
      task.shouldStop();
    }
  }
}