package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;

/**
 * @author Vladimir Kondratyev
 */
public final class ChangeSplitterOrientationAction extends SplitterActionBase {
  public void actionPerformed(final AnActionEvent event) {
    final Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    fileEditorManager.changeSplitterOrientation ();
  }
}
