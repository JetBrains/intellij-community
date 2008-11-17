package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;

/**
 * @author yole
 */
public class JavaBasicWordSelectionFilter implements Condition<PsiElement> {
  public boolean value(final PsiElement e) {
    if (e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType() == JavaTokenType.IDENTIFIER) {
      return true;
    }
    return !(e instanceof PsiCodeBlock) &&
           !(e instanceof PsiArrayInitializerExpression) &&
           !(e instanceof PsiParameterList) &&
           !(e instanceof PsiExpressionList) &&
           !(e instanceof PsiBlockStatement) &&
           !(e instanceof PsiJavaCodeReferenceElement) &&
           !(e instanceof PsiJavaToken &&
           !(e instanceof PsiKeyword)) &&
           !(e instanceof PsiDocTag);
  }
}
