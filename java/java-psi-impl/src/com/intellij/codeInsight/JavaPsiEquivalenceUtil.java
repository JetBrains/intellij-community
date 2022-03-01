// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiDiamondTypeElementImpl;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class JavaPsiEquivalenceUtil {
  public static boolean areExpressionsEquivalent(@NotNull PsiExpression expr1, @NotNull PsiExpression expr2) {
    ResolvedElementsComparator resolvedComparator = new ResolvedElementsComparator();
    LeafElementsComparator leafComparator = new LeafElementsComparator();
    boolean equal = PsiEquivalenceUtil.areElementsEquivalent(expr1, expr2, resolvedComparator, leafComparator);
    if (equal) return true;
    PsiMethodCallExpression call1 = ObjectUtils.tryCast(expr1, PsiMethodCallExpression.class);
    if (call1 == null) return false;
    PsiMethodCallExpression call2 = ObjectUtils.tryCast(expr2, PsiMethodCallExpression.class);
    if (call2 == null) return false;
    if (!isPathConstruction(call1) || !isPathConstruction(call2)) return false;
    PsiExpression[] args1 = call1.getArgumentList().getExpressions();
    PsiExpression[] args2 = call2.getArgumentList().getExpressions();
    if (args1.length != args2.length) return false;
    for (int i = 0; i < args1.length; i++) {
      if (!PsiEquivalenceUtil.areElementsEquivalent(args1[i], args2[i], resolvedComparator, leafComparator)) return false;
    }
    return true;
  }
  
  private static class ResolvedElementsComparator implements Comparator<PsiElement> {
    @Override
    public int compare(PsiElement o1, PsiElement o2) {
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
    }
  }
  
  private static class LeafElementsComparator implements Comparator<PsiElement> {

    @Override
    public int compare(PsiElement o1, PsiElement o2) {
      if (!o1.textMatches(o2)) return 1;

      if (o1 instanceof PsiDiamondTypeElementImpl && o2 instanceof PsiDiamondTypeElementImpl) {
        final PsiDiamondType.DiamondInferenceResult thisInferenceResult = new PsiDiamondTypeImpl(o1.getManager(), (PsiTypeElement)o1).resolveInferredTypes();
        final PsiDiamondType.DiamondInferenceResult otherInferenceResult = new PsiDiamondTypeImpl(o2.getManager(), (PsiTypeElement)o2).resolveInferredTypes();
        return thisInferenceResult.equals(otherInferenceResult) ? 0 : 1;
      }
      return 0;
    }
  }

  private static boolean isPathConstruction(@NotNull PsiMethodCallExpression methodCall) {
    PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
    String name = methodExpression.getReferenceName();
    if ("of".equals(name)) {
      return isMethodFromClass(methodExpression, "Path", "java.nio.file.Path");
    }
    if ("get".equals(name)) {
      return isMethodFromClass(methodExpression, "Paths", "java.nio.file.Paths");
    }
    return false;
  }

  private static boolean isMethodFromClass(@NotNull PsiReferenceExpression methodExpression, 
                                           @NotNull String className, @NotNull String classFqn) {
    PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null || !qualifier.textMatches(className) && !qualifier.textMatches(classFqn)) {
      return false;
    }
    PsiMethod psiMethod = ObjectUtils.tryCast(methodExpression.resolve(), PsiMethod.class);
    if (psiMethod == null) return false;
    PsiClass psiClass = psiMethod.getContainingClass();
    return psiClass != null && classFqn.equals(psiClass.getQualifiedName());
  }
}
