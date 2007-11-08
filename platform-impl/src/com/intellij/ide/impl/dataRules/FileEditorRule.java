package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.ui.EditorTextField;

public class FileEditorRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    final Editor editor = (Editor)dataProvider.getData(DataConstants.EDITOR);
    if (editor == null) return null;

    final Boolean aBoolean = editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY);
    if (aBoolean != null && aBoolean.booleanValue()) return null;

    return TextEditorProvider.getInstance().getTextEditor(editor);
  }
}
