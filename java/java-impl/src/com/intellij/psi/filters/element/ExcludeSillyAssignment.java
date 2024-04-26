// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters.element;

import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.Nullable;

public class ExcludeSillyAssignment implements ElementFilter {

  public static @Nullable PsiReferenceExpression getAssignedReference(PsiElement position) {
    PsiElement each = position;
    while (each != null && !(each instanceof PsiFile)) {
      if (each instanceof PsiExpressionList || each instanceof PsiPrefixExpression || each instanceof PsiPolyadicExpression) {
        return null;
      }

      if (each instanceof PsiAssignmentExpression assignment) {
        final PsiExpression left = assignment.getLExpression();
        if (left instanceof PsiReferenceExpression referenceExpression) {
          final PsiElement qualifier = referenceExpression.getQualifier();
          if (qualifier == null || 
              qualifier instanceof PsiThisExpression thisExpression && thisExpression.getQualifier() == null) {
            return referenceExpression;
          }
        }
        return null;
      }

      each = each.getContext();
    }
    
    return null;
  }
  
  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    if(!(element instanceof PsiElement)) return true;

    PsiReferenceExpression referenceExpression = getAssignedReference(context);
    if (referenceExpression != null && referenceExpression.isReferenceTo((PsiElement)element)) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
