// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
 */
public class UnsplitAction extends SplitterActionBase {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    final Project project = event.getProject();
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    //VirtualFile file = fileEditorManager.getSelectedFiles()[0];
    fileEditorManager.unsplitWindow();
  }
}
