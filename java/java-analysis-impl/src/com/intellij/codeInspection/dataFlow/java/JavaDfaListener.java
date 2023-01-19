// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.java.anchor.*;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JavaDfaListener extends DfaListener {
  @Override
  default void beforePush(@NotNull DfaValue @NotNull [] args,
                          @NotNull DfaValue value,
                          @NotNull DfaAnchor anchor,
                          @NotNull DfaMemoryState state) {
    if (!(anchor instanceof JavaDfaAnchor)) {
      throw new IllegalStateException("Java anchor expected, got: " + anchor + "(" + anchor.getClass() + ")");
    }
    if (anchor instanceof JavaEndOfInstanceInitializerAnchor) {
      beforeInstanceInitializerEnd(state);
      return;
    }
    if (anchor instanceof JavaMethodReferenceReturnAnchor) {
      beforeValueReturn(value, null, ((JavaMethodReferenceReturnAnchor)anchor).getMethodReferenceExpression(), state);
      return;
    }
    if (anchor instanceof JavaExpressionAnchor) {
      PsiExpression psiAnchor = ((JavaExpressionAnchor)anchor).getExpression();
      callBeforeExpressionPush(value, psiAnchor, state, psiAnchor);
    }
    if (anchor instanceof JavaMethodReferenceArgumentAnchor) {
      beforeMethodReferenceArgumentPush(value, ((JavaMethodReferenceArgumentAnchor)anchor).getMethodReference(), state);
    }
  }

  /**
   * Called before instance initializer is finished
   * @param state memory state
   */
  default void beforeInstanceInitializerEnd(DfaMemoryState state) {
    
  }

  private void callBeforeExpressionPush(@NotNull DfaValue value,
                                        @NotNull PsiExpression expression,
                                        @NotNull DfaMemoryState state,
                                        PsiExpression anchor) {
    beforeExpressionPush(value, anchor, state);
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiLambdaExpression) {
      beforeValueReturn(value, expression, parent, state);
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiParameterListOwner context = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class);
      if (context != null) {
        beforeValueReturn(value, expression, context, state);
      }
    }
    else if (anchor instanceof PsiArrayInitializerExpression && parent instanceof PsiNewExpression) {
      callBeforeExpressionPush(value, expression, state, (PsiExpression)parent);
    }
    else if (parent instanceof PsiConditionalExpression &&
             !PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), anchor, false)) {
      callBeforeExpressionPush(value, expression, state, (PsiConditionalExpression)parent);
    }
  }

  /**
   * Called before result of given expression is pushed to the stack
   * @param value value that is about to be pushed
   * @param expression expression evaluated (only completely evaluated expressions are reported here)
   * @param state memory state
   */
  default void beforeExpressionPush(@NotNull DfaValue value,
                                    @NotNull PsiExpression expression,
                                    @NotNull DfaMemoryState state) {

  }

  /**
   * Called before implicit sole argument of method reference is pushed to the stack
   * @param value value that is about to be pushed
   * @param expression corresponding method reference
   * @param state memory state
   */
  default void beforeMethodReferenceArgumentPush(@NotNull DfaValue value,
                                                 @NotNull PsiMethodReferenceExpression expression,
                                                 @NotNull DfaMemoryState state) {

  }

  /**
   * Called before returning the value from specific computation scope (method, lambda, method reference).
   * Can be called many times for the same scope.
   *
   * @param value      value to be returned
   * @param expression expression that resulted in a given value (can be null)
   * @param context    context ({@link PsiMethod}, or {@link PsiFunctionalExpression})
   * @param state      memory state
   */
  default void beforeValueReturn(@NotNull DfaValue value,
                                 @Nullable PsiExpression expression,
                                 @NotNull PsiElement context,
                                 @NotNull DfaMemoryState state) {

  }
}
