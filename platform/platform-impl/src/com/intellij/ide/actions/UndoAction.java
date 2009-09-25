package com.intellij.ide.actions;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;

public class UndoAction extends UndoRedoAction implements DumbAware {
  @Override
  protected boolean isAvailable(FileEditor editor, UndoManager undoManager) {
    return undoManager.isUndoAvailable(editor);
  }

  @Override
  protected void perform(FileEditor editor, UndoManager undoManager) {
    undoManager.undo(editor);
  }

  @Override
  protected String formatAction(FileEditor editor, UndoManager undoManager) {
    return undoManager.formatAvailableUndoAction(editor);
  }

  protected String getActionMessageKey() {
    return "action.$Undo.text";
  }

  @Override
  protected String getActionDescriptionMessageKey() {
    return "action.$Undo.description";
  }

  @Override
  protected String getActionDescriptionEmptyMessageKey() {
    return "action.$Undo.description.empty";
  }
}
