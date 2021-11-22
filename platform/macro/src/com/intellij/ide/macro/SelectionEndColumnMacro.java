// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;


public class SelectionEndColumnMacro extends EditorMacro {
  public SelectionEndColumnMacro() {
    super("SelectionEndColumn", ExecutionBundle.message("ide.macro.selected.text.end.column.number"));
  }

  @Override
  protected String expand(Editor editor) {
    VisualPosition selectionEndPosition = editor.getSelectionModel().getSelectionEndPosition();
    if (selectionEndPosition == null) {
      return null;
    }
    return getColumnNumber(editor, editor.visualToLogicalPosition(selectionEndPosition));
  }
}
