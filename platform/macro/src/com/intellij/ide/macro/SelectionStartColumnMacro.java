// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;


public class SelectionStartColumnMacro extends EditorMacro {
  public SelectionStartColumnMacro() {
    super("SelectionStartColumn", ExecutionBundle.message("ide.macro.selected.text.start.column.number"));
  }

  @Override
  protected String expand(Editor editor) {
    VisualPosition selectionStartPosition = editor.getSelectionModel().getSelectionStartPosition();
    if (selectionStartPosition == null) {
      return null;
    }
    return getColumnNumber(editor, editor.visualToLogicalPosition(selectionStartPosition));
  }
}
