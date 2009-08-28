package com.intellij.ide.impl.dataRules;

import com.intellij.ide.CutProvider;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.ex.EditorEx;

public class CutProviderRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    EditorEx editor = (EditorEx)dataProvider.getData(DataConstants.EDITOR);
    if (editor == null) return null;
    CutProvider cutProvider = editor.getCutProvider();
    return cutProvider;
  }
}
