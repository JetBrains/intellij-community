// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;


public class SelectionEndColumnMacro extends EditorMacro {
  public SelectionEndColumnMacro() {
    super("SelectionEndColumn");
  }

  @Override
  public @NotNull String getDescription() {
    return ExecutionBundle.message("ide.macro.selected.text.end.column.number");
  }

  @Override
  protected String expand(Editor editor) {
    return getColumnNumber(editor, editor.getSelectionModel().getSelectionEnd());
  }
}
