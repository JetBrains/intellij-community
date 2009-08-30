package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlElement;
import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;

public class BasicSelectioner extends ExtendWordSelectionHandlerBase {

  public boolean canSelect(PsiElement e) {
    return canSelectBasic(e);
  }

  public static boolean canSelectBasic(final PsiElement e) {
    return
      !(e instanceof PsiWhiteSpace) &&
      !(e instanceof PsiComment) &&
      !(e instanceof PsiCodeBlock) &&
      !(e instanceof PsiArrayInitializerExpression) &&
      !(e instanceof PsiParameterList) &&
      !(e instanceof PsiExpressionList) &&
      !(e instanceof PsiBlockStatement) &&
      !(e instanceof PsiJavaCodeReferenceElement) &&
      !(e instanceof PsiJavaToken &&
      !(e instanceof PsiKeyword)) &&
      !(e instanceof XmlToken) &&
      !(e instanceof XmlElement) &&
      !(e instanceof PsiDocTag);
  }
}
