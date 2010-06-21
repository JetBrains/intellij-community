/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Pair;

public class RedoAction extends UndoRedoAction {
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
