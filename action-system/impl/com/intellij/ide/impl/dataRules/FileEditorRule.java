package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;

public class FileEditorRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    final Editor editor = (Editor)dataProvider.getData(DataConstants.EDITOR);
    if (editor == null) {
      return null;
    }

    return TextEditorProvider.getInstance().getTextEditor(editor);
  }
}
