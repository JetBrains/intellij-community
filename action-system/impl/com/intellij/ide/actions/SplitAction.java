package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * @author Vladimir Kondratyev
 */
public abstract class SplitAction extends AnAction{
  private final int myOrientation;

  protected SplitAction(final int orientation){
    myOrientation = orientation;
  }

  public void actionPerformed(final AnActionEvent event) {
    final Project project = DataKeys.PROJECT.getData(event.getDataContext());
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    fileEditorManager.createSplitter (myOrientation);
  }

  public void update(final AnActionEvent event) {
    final Project project = DataKeys.PROJECT.getData(event.getDataContext());
    final Presentation presentation = event.getPresentation();
    presentation.setText (myOrientation == SwingConstants.VERTICAL
                          ? IdeBundle.message("action.split.vertically")
                          : IdeBundle.message("action.split.horizontally"));
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    presentation.setEnabled(fileEditorManager.hasOpenedFile ());
  }
}
