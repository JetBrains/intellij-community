// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

public final class ColumnNumberMacro extends EditorMacro {
  public ColumnNumberMacro() {
    super("ColumnNumber");
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.column.number");
  }

  @Override
  protected String expand(Editor editor) {
    return getColumnNumber(editor, editor.getCaretModel().getLogicalPosition());
  }
}
