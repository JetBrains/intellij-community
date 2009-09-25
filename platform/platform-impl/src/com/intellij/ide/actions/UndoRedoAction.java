package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

public abstract class UndoRedoAction extends AnAction implements DumbAware {
  public UndoRedoAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);
    UndoManager undoManager = getUndoManager(editor, dataContext);

    perform(editor, undoManager);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    FileEditor editor = PlatformDataKeys.FILE_EDITOR.getData(dataContext);

    // do not allow global undo in dialogs
    if (editor == null && dataContext.getData(DataConstants.IS_MODAL_CONTEXT) == Boolean.TRUE){
      presentation.setEnabled(false);
      return;
    }

    UndoManager undoManager = getUndoManager(editor, dataContext);
    boolean available = isAvailable(editor, undoManager);
    presentation.setEnabled(available);
    String actionName = available ? formatAction(editor, undoManager) : "";
    String shortActionName = StringUtil.first(actionName, 30, true);
    if (actionName.length() == 0) actionName = ActionsBundle.message(getActionDescriptionEmptyMessageKey());

    presentation.setText(ActionsBundle.message(getActionMessageKey(), shortActionName).trim());
    presentation.setDescription(ActionsBundle.message(getActionDescriptionMessageKey(), actionName).trim());
  }

  private static UndoManager getUndoManager(FileEditor editor, DataContext dataContext) {
    Project project = getProject(editor, dataContext);
    return project != null ? UndoManager.getInstance(project) : UndoManager.getGlobalInstance();
  }

  private static Project getProject(FileEditor editor, DataContext dataContext) {
    Project project;
    if (editor instanceof TextEditor){
      project = ((TextEditor)editor).getEditor().getProject();
    }
    else {
      project = PlatformDataKeys.PROJECT.getData(dataContext);
    }
    return project;
  }

  protected abstract boolean isAvailable(FileEditor editor, UndoManager undoManager);

  protected abstract void perform(FileEditor editor, UndoManager undoManager);

  protected abstract String getActionMessageKey();

  protected abstract String getActionDescriptionMessageKey();

  protected abstract String getActionDescriptionEmptyMessageKey();

  protected abstract String formatAction(FileEditor editor, UndoManager undoManager);
}