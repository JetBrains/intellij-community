package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;

public class FileEditorRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    final Editor element = (Editor)dataProvider.getData(DataConstants.EDITOR);
    if (element == null) {
      return null;
    }

    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(element);
    return textEditor;
  }
}
