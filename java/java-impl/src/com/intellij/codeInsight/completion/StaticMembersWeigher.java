/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class StaticMembersWeigher extends CompletionWeigher {
  public Comparable weigh(@NotNull LookupElement element, CompletionLocation loc) {
    if (loc.getCompletionType() != CompletionType.BASIC) return 0;

    final PsiElement position = loc.getCompletionParameters().getPosition();
    if (!position.isValid()) return 0;

    if (PsiTreeUtil.getParentOfType(position, PsiDocComment.class) != null) return 0;
    if (position.getParent() instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)position.getParent();
      final PsiElement qualifier = refExpr.getQualifier();
      if (qualifier == null) {
        return 0;
      }
      if (!(qualifier instanceof PsiJavaCodeReferenceElement) || !(((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass)) {
        return 0;
      }
    }

    final Object o = element.getObject();
    if (!(o instanceof PsiMember)) return 0;

    if (((PsiMember)o).hasModifierProperty(PsiModifier.STATIC)) {
      if (o instanceof PsiMethod) return 5;
      if (o instanceof PsiField) return 4;
    }

    if (o instanceof PsiClass && ((PsiClass) o).getContainingClass() != null) {
      return 3;
    }

    //instance method or field
    return 5;
  }
}
