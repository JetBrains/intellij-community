// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;


public class SelectionEndLineMacro extends EditorMacro {
  public SelectionEndLineMacro() {
    super("SelectionEndLine");
  }

  @Override
  public @NotNull String getDescription() {
    return ExecutionBundle.message("ide.macro.selected.text.end.line.number");
  }

  @Override
  protected String expand(Editor editor) {
    return String.valueOf(getLineNumber(editor, editor.getSelectionModel().getSelectionEnd()) + 1);
  }
}
