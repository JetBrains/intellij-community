package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;

/**
 * @author Vladimir Kondratyev
 */
public class UnsplitAction extends SplitterActionBase {
  public void actionPerformed(final AnActionEvent event) {
    final Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    //VirtualFile file = fileEditorManager.getSelectedFiles()[0];
    fileEditorManager.unsplitWindow();
  }
}
