/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiJavaPatterns extends StandardPatterns{

  public static IElementTypePattern elementType() {
    return PlatformPatterns.elementType();
  }

  public static VirtualFilePattern virtualFile() {
    return PlatformPatterns.virtualFile();
  }

  public static PsiJavaElementPattern.Capture<PsiElement> psiElement() {
    return new PsiJavaElementPattern.Capture<PsiElement>(PsiElement.class);
  }

  public static PsiJavaElementPattern.Capture<PsiElement> psiElement(IElementType type) {
    return psiElement().withElementType(type);
  }

  public static <T extends PsiElement> PsiJavaElementPattern.Capture<T> psiElement(final Class<T> aClass) {
    return new PsiJavaElementPattern.Capture<T>(aClass);
  }

  public static PsiJavaElementPattern.Capture<PsiLiteralExpression> literalExpression() {
    return new PsiJavaElementPattern.Capture<PsiLiteralExpression>(new NullablePatternCondition() {
      public boolean accepts(@Nullable final Object o, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return o instanceof PsiLiteralExpression;
      }
    });
  }

  public static PsiMemberPattern.Capture psiMember() {
    return new PsiMemberPattern.Capture();
  }

  public static PsiMethodPattern psiMethod() {
    return new PsiMethodPattern();
  }

  public static PsiFieldPattern psiField() {
    return new PsiFieldPattern();
  }

  public static PsiClassPattern psiClass() {
    return new PsiClassPattern();
  }

  public static PsiAnnotationPattern psiAnnotation() {
    return new PsiAnnotationPattern();
  }

  public static PsiNameValuePairPattern psiNameValuePair() {
    return new PsiNameValuePairPattern();
  }

  public static PsiExpressionPattern.Capture<PsiExpression> psiExpression() {
    return new PsiExpressionPattern.Capture<PsiExpression>(PsiExpression.class);
  }

  public static PsiBinaryExpressionPattern psiBinaryExpression() {
    return new PsiBinaryExpressionPattern();
  }

  public static PsiJavaElementPattern.Capture<PsiReferenceExpression> psiReferenceExpression() {
    return psiElement(PsiReferenceExpression.class);
  }

  public static PsiJavaElementPattern.Capture<PsiExpressionStatement> psiExpressionStatement() {
    return psiElement(PsiExpressionStatement.class);
  }
}
