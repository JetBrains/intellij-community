// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.editor.Editor;


public class SelectedTextMacro extends EditorMacro {
  public SelectedTextMacro() {
    super("SelectedText", IdeCoreBundle.message("ide.macro.text.selected.in.the.editor"));
  }

  @Override
  protected String expand(Editor editor) {
    return editor.getSelectionModel().getSelectedText();
  }
}
