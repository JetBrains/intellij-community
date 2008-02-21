package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.psi.PsiElement;

public class PsiElementFromSelectionRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    final Object element = dataProvider.getData(DataConstants.SELECTED_ITEM);
    if (element instanceof PsiElement) {
      return element;
    }

    return null;
  }
}