// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;


public class JavaBasicWordSelectionFilter implements Condition<PsiElement> {
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
