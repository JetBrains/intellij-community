
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CloseEditorAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);

    final FileEditorManagerEx editorManager = ((FileEditorManagerEx)FileEditorManager.getInstance(project));
    EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    VirtualFile file;
    if (window == null) {
      window = editorManager.getCurrentWindow();
      file = window.getSelectedFile();
    }
    else {
      file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
    }
    if (file != null) {
      editorManager.closeFile(file, window);
    }
  }

  public void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace()) || ActionPlaces.EDITOR_TAB_POPUP.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.close"));
    }
    EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (window == null) {
      window = ((FileEditorManagerEx)FileEditorManager.getInstance(project)).getCurrentWindow();
    }
    presentation.setEnabled(window != null && window.getTabCount() > 0);
  }
}
