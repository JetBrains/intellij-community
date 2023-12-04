// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.editor.Editor;


public class SelectionEndLineMacro extends EditorMacro {
  public SelectionEndLineMacro() {
    super("SelectionEndLine", ExecutionBundle.message("ide.macro.selected.text.end.line.number"));
  }

  @Override
  protected String expand(Editor editor) {
    return String.valueOf(getLineNumber(editor, editor.getSelectionModel().getSelectionEnd()) + 1);
  }
}
