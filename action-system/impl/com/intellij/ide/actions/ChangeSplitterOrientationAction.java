package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;

/**
 * @author Vladimir Kondratyev
 */
public final class ChangeSplitterOrientationAction extends AnAction{
  public void actionPerformed(final AnActionEvent event) {
    final Project project = DataKeys.PROJECT.getData(event.getDataContext());
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    fileEditorManager.changeSplitterOrientation ();
  }

  public void update(final AnActionEvent event) {
    final Project project = DataKeys.PROJECT.getData(event.getDataContext());
    final Presentation presentation = event.getPresentation();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setText(IdeBundle.message("action.change.splitter.orientations"));
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    presentation.setEnabled (fileEditorManager.isInSplitter());
  }
}
