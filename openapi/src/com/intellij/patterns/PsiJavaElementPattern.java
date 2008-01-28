/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiJavaElementPattern<T extends PsiElement,Self extends PsiJavaElementPattern<T,Self>> extends PsiElementPattern<T,Self> {
  public PsiJavaElementPattern(final Class<T> aClass) {
    super(aClass);
  }

  public PsiJavaElementPattern(@NotNull final InitialPatternCondition<T> condition) {
    super(condition);
  }

  public Self annotationParam(@NonNls final String annotationQualifiedName, @NonNls final String parameterName) {
    return annotationParam(StandardPatterns.string().equalTo(annotationQualifiedName), parameterName);
  }

  public Self annotationParam(final ElementPattern annotationQualifiedName, @NonNls final String parameterName) {
    return withParent(
      PsiJavaPatterns.psiNameValuePair().withName(parameterName).withParent(
        PlatformPatterns.psiElement(PsiAnnotationParameterList.class).withParent(
          PsiJavaPatterns.psiAnnotation().qName(annotationQualifiedName))));
  }

  public Self insideAnnotationParam(final ElementPattern annotationQualifiedName, @NonNls final String parameterName) {
    return inside(true,
      PsiJavaPatterns.psiNameValuePair().withName(parameterName).withParent(
        PlatformPatterns.psiElement(PsiAnnotationParameterList.class).withParent(
          PsiJavaPatterns.psiAnnotation().qName(annotationQualifiedName))));
  }

  public Self nameIdentifierOf(final Class<? extends PsiMember> aClass) {
    return nameIdentifierOf(PsiJavaPatterns.instanceOf(aClass));
  }
  
  public Self nameIdentifierOf(final ElementPattern<? extends PsiMember> pattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        if (!(t instanceof PsiIdentifier)) return false;

        final PsiElement parent = t.getParent();
        if (parent instanceof PsiClass && t != ((PsiClass) parent).getNameIdentifier()) return false;
        if (parent instanceof PsiMethod && t != ((PsiMethod) parent).getNameIdentifier()) return false;
        if (parent instanceof PsiVariable && t != ((PsiVariable) parent).getNameIdentifier()) return false;

        return pattern.getCondition().accepts(parent, matchingContext, traverseContext);
      }
    });
  }

  public Self methodCallParameter(final int index, final ElementPattern<? extends PsiMethod> methodPattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T literal, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        final PsiElement parent = literal.getParent();
        if (parent instanceof PsiExpressionList) {
          final PsiExpressionList psiExpressionList = (PsiExpressionList)parent;
          final PsiExpression[] psiExpressions = psiExpressionList.getExpressions();
          if (!(psiExpressions.length > index && psiExpressions[index] == literal)) return false;

          final PsiElement element = psiExpressionList.getParent();
          if (element instanceof PsiMethodCallExpression) {
            final JavaResolveResult[] results = ((PsiMethodCallExpression)element).getMethodExpression().multiResolve(true);
            for (JavaResolveResult result : results) {
              final PsiElement psiElement = result.getElement();
              if (methodPattern.getCondition().accepts(psiElement, matchingContext, traverseContext)) {
                return true;
              }
            }
          }
        }
        return false;
      }
    });
  }

  public static class Capture<T extends PsiElement> extends PsiJavaElementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

    public Capture(@NotNull final InitialPatternCondition<T> condition) {
      super(condition);
    }
  }



}