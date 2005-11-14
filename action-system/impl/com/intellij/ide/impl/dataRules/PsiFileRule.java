package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class PsiFileRule implements GetDataRule {
  public Object getData(DataProvider dataProvider) {
    final PsiElement element = (PsiElement)dataProvider.getData(DataConstants.PSI_ELEMENT);
    if (element == null) return null;
    if (element instanceof PsiFile) return element;
    return element.getContainingFile();
  }
}
