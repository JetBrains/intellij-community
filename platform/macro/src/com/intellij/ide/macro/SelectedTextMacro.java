// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;


public class SelectedTextMacro extends EditorMacro {
  public SelectedTextMacro() {
    super("SelectedText");
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("ide.macro.text.selected.in.the.editor");
  }

  @Override
  protected String expand(Editor editor) {
    return editor.getSelectionModel().getSelectedText();
  }
}
