// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;


public final class JavaBasicWordSelectionFilter implements Condition<PsiElement> {

  public JavaBasicWordSelectionFilter() {
  }

  @Override
  public boolean value(final PsiElement e) {
    return !(e instanceof PsiCodeBlock) &&
           !(e instanceof PsiArrayInitializerExpression) &&
           !(e instanceof PsiParameterList) &&
           !(e instanceof PsiExpressionList) &&
           !(e instanceof PsiBlockStatement) &&
           !(e instanceof PsiJavaCodeReferenceElement) &&
           !(e instanceof PsiJavaToken) &&
           !(e instanceof PsiDocTag) &&
           !(e instanceof PsiDocToken && ((PsiDocToken)e).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA);
  }
}
