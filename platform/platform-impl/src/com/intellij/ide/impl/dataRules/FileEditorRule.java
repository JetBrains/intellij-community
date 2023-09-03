// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.NotNull;

public final class FileEditorRule implements GetDataRule {
  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    final Editor editor = (Editor)dataProvider.getData(CommonDataKeys.EDITOR.getName());
    if (editor == null || editor.isDisposed()) {
      return null;
    }

    final Boolean aBoolean = editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY);
    if (aBoolean != null && aBoolean.booleanValue()) {
      return null;
    }

    return TextEditorProvider.getInstance().getTextEditor(editor);
  }
}
