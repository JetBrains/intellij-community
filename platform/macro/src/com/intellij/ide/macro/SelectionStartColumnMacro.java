// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;


public class SelectionStartColumnMacro extends EditorMacro {
  public SelectionStartColumnMacro() {
    super("SelectionStartColumn");
  }

  @Override
  @NotNull
  public String getDescription() {
    return ExecutionBundle.message("ide.macro.selected.text.start.column.number");
  }

  @Override
  protected String expand(Editor editor) {
    return getColumnNumber(editor, editor.getSelectionModel().getSelectionStart());
  }
}
