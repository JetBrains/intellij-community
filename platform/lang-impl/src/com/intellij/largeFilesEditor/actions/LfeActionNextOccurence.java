// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.actions;

import com.intellij.largeFilesEditor.editor.EditorManager;
import com.intellij.largeFilesEditor.search.SearchManager;
import com.intellij.largeFilesEditor.search.searchTask.CloseSearchTask;
import com.intellij.largeFilesEditor.search.searchTask.SearchTaskBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class LfeActionNextOccurence extends LfeBaseProxyAction {

  private final boolean isForwardDirection = isForwardDirection();

  public LfeActionNextOccurence(AnAction originalAction) {
    super(originalAction);
  }

  @Override
  protected void updateForLfe(AnActionEvent e, @NotNull EditorManager editorManager) {
    SearchManager searchManager = editorManager.getSearchManager();
    SearchTaskBase task = searchManager.getLastExecutedSearchTask();
    boolean enabled = !(task instanceof CloseSearchTask) || task.isFinished();
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  protected void actionPerformedForLfe(AnActionEvent e, @NotNull EditorManager editorManager) {
    SearchManager searchManager = editorManager.getSearchManager();
    searchManager.gotoNextOccurrence(isForwardDirection);
  }

  protected boolean isForwardDirection() {
    return true;
  }
}
