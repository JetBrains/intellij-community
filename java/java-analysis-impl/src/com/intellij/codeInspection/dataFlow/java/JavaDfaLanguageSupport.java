// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.lang.DfaInterceptor;
import com.intellij.codeInspection.dataFlow.lang.DfaLanguageSupport;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.EnsureInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.MethodReferenceInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class JavaDfaLanguageSupport implements DfaLanguageSupport<PsiExpression> {
  @Override
  public void processExpressionPush(@NotNull DfaInterceptor<PsiExpression> interceptor,
                                    @NotNull DfaValue value,
                                    @NotNull ExpressionPushingInstruction<?> instruction,
                                    @NotNull DfaMemoryState state) {
    PsiElement expression = instruction.getExpression();
    if (expression == null) return;
    if (!(expression instanceof PsiExpression)) {
      throw new IllegalStateException("PsiExpression expected, got: " + expression + "(" + expression.getClass() + ")");
    }
    PsiExpression anchor = (PsiExpression)expression;
    if (isExpressionPush(instruction, anchor)) {
      if (anchor instanceof PsiMethodReferenceExpression && !(instruction instanceof MethodReferenceInstruction)) {
        interceptor.beforeValueReturn(value, null, anchor, state);
      }
      else {
        callBeforeExpressionPush(interceptor, value, instruction, anchor, state, anchor);
      }
    }
  }

  @Override
  public void processConditionFailure(@NotNull DfaInterceptor<? super PsiExpression> interceptor,
                                      @NotNull DfaValue tosValue, @NotNull EnsureInstruction instruction,
                                      boolean alwaysFails) {
    PsiExpression expression = ObjectUtils.tryCast(instruction.getPsiAnchor(), PsiExpression.class);
    if (expression != null) {
      interceptor.onConditionFailure(expression, tosValue, alwaysFails);
    }
  }

  private static boolean isExpressionPush(@NotNull ExpressionPushingInstruction<?> instruction, PsiExpression anchor) {
    if (anchor == null) return false;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      if (assignment.getOperationTokenType().equals(JavaTokenType.EQ) &&
          PsiTreeUtil.isAncestor(assignment.getLExpression(), anchor, false)) {
        return false;
      }
    }
    if (instruction instanceof PushInstruction) {
      return !((PushInstruction)instruction).isReferenceWrite();
    }
    return true;
  }

  private static void callBeforeExpressionPush(@NotNull DfaInterceptor<PsiExpression> interceptor,
                                               @NotNull DfaValue value,
                                               @NotNull ExpressionPushingInstruction<?> instruction,
                                               @NotNull PsiExpression expression,
                                               @NotNull DfaMemoryState state,
                                               PsiExpression anchor) {
    interceptor.beforeExpressionPush(value, anchor, instruction.getExpressionRange(), state);
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiLambdaExpression) {
      interceptor.beforeValueReturn(value, expression, parent, state);
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiParameterListOwner context = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class);
      if (context != null) {
        interceptor.beforeValueReturn(value, expression, context, state);
      }
    }
    else if (anchor instanceof PsiArrayInitializerExpression && parent instanceof PsiNewExpression) {
      callBeforeExpressionPush(interceptor, value, instruction, expression, state, (PsiExpression)parent);
    }
    else if (parent instanceof PsiConditionalExpression &&
             !PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), anchor, false)) {
      callBeforeExpressionPush(interceptor, value, instruction, expression, state, (PsiConditionalExpression)parent);
    }
    else if (parent instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)parent;
      if ((polyadic.getOperationTokenType().equals(JavaTokenType.ANDAND) || polyadic.getOperationTokenType().equals(JavaTokenType.OROR)) &&
          PsiTreeUtil.isAncestor(ArrayUtil.getLastElement(polyadic.getOperands()), anchor, false)) {
        callBeforeExpressionPush(interceptor, value, instruction, expression, state, polyadic);
      }
    }
  }
}
