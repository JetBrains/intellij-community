package com.intellij.ide.actions;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;

public class RedoAction extends UndoRedoAction implements DumbAware {
  @Override
  protected boolean isAvailable(FileEditor editor, UndoManager undoManager) {
    return undoManager.isRedoAvailable(editor);
  }

  @Override
  protected void perform(FileEditor editor, UndoManager undoManager) {
    undoManager.redo(editor);
  }

  @Override
  protected String formatAction(FileEditor editor, UndoManager undoManager) {
    return undoManager.formatAvailableRedoAction(editor);
  }

  protected String getActionMessageKey() {
    return "action.$Redo.text";
  }

  @Override
  protected String getActionDescriptionMessageKey() {
    return "action.$Redo.description";
  }

  @Override
  protected String getActionDescriptionEmptyMessageKey() {
    return "action.$Redo.description.empty";
  }
}
