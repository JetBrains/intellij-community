package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;

public class RedoAction extends AnAction {
  public RedoAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    FileEditor editor = DataKeys.FILE_EDITOR.getData(dataContext);

    Project project = getProject(editor, dataContext);
    UndoManager undoManager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
    undoManager.redo(editor);
  }

  private Project getProject(FileEditor editor, DataContext dataContext) {
    final Project project;
    if (editor instanceof TextEditor){
      project = ((TextEditor)editor).getEditor().getProject();
    }
    else {
      project = DataKeys.PROJECT.getData(dataContext);
    }
    return project;
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    FileEditor editor = DataKeys.FILE_EDITOR.getData(dataContext);

    // do not allow global redo in dialogs
    if (editor == null && dataContext.getData(DataConstants.IS_MODAL_CONTEXT) == Boolean.TRUE){
      presentation.setEnabled(false);
      return;
    }

    Project project = getProject(editor, dataContext);
    UndoManager undoManager = project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
    boolean b = undoManager.isRedoAvailable(editor);
    presentation.setEnabled(b);
  }
}
