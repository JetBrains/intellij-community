// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiDiamondTypeElementImpl;
import org.jetbrains.annotations.NotNull;

public final class JavaPsiEquivalenceUtil {
  public static boolean areExpressionsEquivalent(@NotNull PsiExpression expr1, @NotNull PsiExpression expr2) {
    return PsiEquivalenceUtil.areElementsEquivalent(expr1, expr2, (o1, o2) -> {
      if (o1 instanceof PsiParameter && o2 instanceof PsiParameter) {
        final PsiElement scope1 = ((PsiParameter)o1).getDeclarationScope();
        final PsiElement scope2 = ((PsiParameter)o2).getDeclarationScope();
        if (scope1 instanceof PsiMethod && scope2 instanceof PsiMethod ||
            scope1 instanceof PsiLambdaExpression && scope2 instanceof PsiLambdaExpression) {
          if (!scope1.getTextRange().intersects(scope2.getTextRange())) {
            return ((PsiParameter)o1).getName().compareTo(((PsiParameter)o2).getName());
          }
        }
      }
      return 1;
    }, (o1, o2) -> {
      if (!o1.textMatches(o2)) return 1;

      if (o1 instanceof PsiDiamondTypeElementImpl && o2 instanceof PsiDiamondTypeElementImpl) {
        final PsiDiamondType.DiamondInferenceResult thisInferenceResult = new PsiDiamondTypeImpl(o1.getManager(), (PsiTypeElement)o1).resolveInferredTypes();
        final PsiDiamondType.DiamondInferenceResult otherInferenceResult = new PsiDiamondTypeImpl(o2.getManager(), (PsiTypeElement)o2).resolveInferredTypes();
        return thisInferenceResult.equals(otherInferenceResult) ? 0 : 1;
      }
      return 0;
    });
  }
}
