package com.intellij.ide.impl.dataRules;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.ex.EditorEx;

public class CopyProviderRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    EditorEx editor = (EditorEx)dataProvider.getData(DataConstants.EDITOR);
    if (editor == null) return null;
    CopyProvider copyProvider = editor.getCopyProvider();
    return copyProvider;
  }
}
