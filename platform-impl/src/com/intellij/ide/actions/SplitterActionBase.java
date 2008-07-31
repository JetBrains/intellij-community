package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;

/**
 * @author yole
 */
public abstract class SplitterActionBase extends AnAction {
  public void update(final AnActionEvent event) {
    final Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    final Presentation presentation = event.getPresentation();
    boolean enabled;
    if (project == null) {
      enabled = false;
    }
    else {
      final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
      enabled = fileEditorManager.isInSplitter();
    }
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(enabled);
    }
    else {
      presentation.setEnabled(enabled);
    }
  }
}
