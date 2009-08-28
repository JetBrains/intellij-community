package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;

public class WordSelectioner extends AbstractWordSelectioner {
  private static final ExtensionPointName<Condition<PsiElement>> EP_NAME = ExtensionPointName.create("com.intellij.basicWordSelectionFilter");

  public boolean canSelect(PsiElement e) {
    if (e instanceof PsiComment || e instanceof PsiWhiteSpace) {
      return false;
    }
    for (Condition<PsiElement> filter : Extensions.getExtensions(EP_NAME)) {
      if (!filter.value(e)) {
        return false;
      }
    }
    return true;
  }
}
