// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Pair;

public final class RedoAction extends UndoRedoAction implements LightEditCompatible {
  @Override
  protected boolean isAvailable(FileEditor editor, UndoManager undoManager) {
    return undoManager.isRedoAvailable(editor);
  }

  @Override
  protected void perform(FileEditor editor, UndoManager undoManager) {
    undoManager.redo(editor);
  }

  @Override
  protected Pair<String, String> getActionNameAndDescription(FileEditor editor, UndoManager undoManager) {
    return undoManager.getRedoActionNameAndDescription(editor);
  }
}
