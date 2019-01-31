// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
 */
public final class UnsplitAllAction extends SplitterActionBase {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    //VirtualFile file = fileEditorManager.getSelectedFiles()[0];
    fileEditorManager.unsplitAllWindow();
  }

  @Override
  protected boolean isActionEnabled(Project project, boolean inContextMenu) {
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    return inContextMenu ? fileEditorManager.getWindowSplitCount() > 2 : fileEditorManager.isInSplitter();
  }
}
