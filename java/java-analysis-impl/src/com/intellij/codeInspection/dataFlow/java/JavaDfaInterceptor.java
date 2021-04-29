// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaPolyadicPartAnchor;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaSwitchLabelTakenAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.DfaInterceptor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JavaDfaInterceptor extends DfaInterceptor {
  @Override
  default void beforePush(@NotNull DfaValue value,
                          @NotNull DfaAnchor anchor,
                          @NotNull DfaMemoryState state) {
    if (anchor instanceof JavaPolyadicPartAnchor || anchor instanceof JavaSwitchLabelTakenAnchor) {
      // do not report by default
      return;
    }
    if (anchor instanceof JavaMethodReferenceReturnAnchor) {
      beforeValueReturn(value, (PsiExpression)null, ((JavaMethodReferenceReturnAnchor)anchor).getMethodReferenceExpression(), state);
      return;
    }
    if (!(anchor instanceof JavaExpressionAnchor)) {
      throw new IllegalStateException("Java anchor expected, got: " + anchor + "(" + anchor.getClass() + ")");
    }
    PsiExpression psiAnchor = ((JavaExpressionAnchor)anchor).getExpression();
    callBeforeExpressionPush(value, psiAnchor, state, psiAnchor);
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
    else if (parent instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)parent;
      if ((polyadic.getOperationTokenType().equals(JavaTokenType.ANDAND) || polyadic.getOperationTokenType().equals(JavaTokenType.OROR)) &&
          PsiTreeUtil.isAncestor(ArrayUtil.getLastElement(polyadic.getOperands()), anchor, false)) {
        callBeforeExpressionPush(value, expression, state, polyadic);
      }
    }
  }

  default void beforeExpressionPush(@NotNull DfaValue value,
                                    @NotNull PsiExpression expression,
                                    @NotNull DfaMemoryState state) {

  }

  @Override
  default void beforeValueReturn(@NotNull DfaValue value,
                                 @Nullable DfaAnchor anchor,
                                 @NotNull PsiElement context,
                                 @NotNull DfaMemoryState state) {
    if (anchor instanceof JavaExpressionAnchor) {
      beforeValueReturn(value, ((JavaExpressionAnchor)anchor).getExpression(), context, state);
    }
    else if (anchor == null) {
      beforeValueReturn(value, (PsiExpression)null, context, state);
    }
    else {
      throw new IllegalStateException("Java anchor expected, got: " + anchor + "(" + anchor.getClass() + ")");
    }
  }

  default void beforeValueReturn(@NotNull DfaValue value,
                                 @Nullable PsiExpression expression,
                                 @NotNull PsiElement context,
                                 @NotNull DfaMemoryState state) {

  }
}
