/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public enum Mutability {
  UNKNOWN, MUTABLE, UNMODIFIABLE, UNMODIFIABLE_VIEW;

  public static final String UNMODIFIABLE_ANNOTATION = "org.jetbrains.annotations.Unmodifiable";
  public static final String UNMODIFIABLE_VIEW_ANNOTATION = "org.jetbrains.annotations.UnmodifiableView";

  public boolean isUnmodifiable() {
    return this == UNMODIFIABLE || this == UNMODIFIABLE_VIEW;
  }

  @NotNull
  public static Mutability getMutability(@NotNull PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList) {
      PsiParameterList list = (PsiParameterList)owner.getParent();
      PsiMethod method = ObjectUtils.tryCast(list.getParent(), PsiMethod.class);
      if (method != null) {
        int index = list.getParameterIndex((PsiParameter)owner);
        MutationSignature signature = MutationSignature.fromMethod(method);
        if (signature.mutatesArg(index)) {
          return MUTABLE;
        } else if (signature.preservesArg(index) &&
                   PsiTreeUtil.findChildOfAnyType(method.getBody(), PsiLambdaExpression.class, PsiClass.class) == null) {
          // If method preserves argument, it still may return a lambda which captures an argument and changes it
          // TODO: more precise check (at least differentiate parameters which are captured by lambdas or not)
          return UNMODIFIABLE_VIEW;
        }
        return UNKNOWN;
      }
    }
    if (AnnotationUtil.isAnnotated(owner, Collections.singleton(UNMODIFIABLE_ANNOTATION),
                                   AnnotationUtil.CHECK_HIERARCHY |
                                   AnnotationUtil.CHECK_EXTERNAL |
                                   AnnotationUtil.CHECK_INFERRED)) {
      return UNMODIFIABLE;
    }
    if (AnnotationUtil.isAnnotated(owner, Collections.singleton(UNMODIFIABLE_VIEW_ANNOTATION),
                                   AnnotationUtil.CHECK_HIERARCHY |
                                   AnnotationUtil.CHECK_EXTERNAL |
                                   AnnotationUtil.CHECK_INFERRED)) {
      return UNMODIFIABLE_VIEW;
    }
    if (owner instanceof PsiField && owner.hasModifierProperty(PsiModifier.FINAL)) {
      PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(((PsiField)owner).getInitializer());
      if (initializer instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)initializer).resolveMethod();
        return method == null ? UNKNOWN : getMutability(method);
      }
    }
    return UNKNOWN;
  }
}
