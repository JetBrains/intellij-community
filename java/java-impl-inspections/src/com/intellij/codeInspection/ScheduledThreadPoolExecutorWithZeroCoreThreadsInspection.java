// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScheduledThreadPoolExecutorWithZeroCoreThreadsInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final String SCHEDULED_THREAD_POOL_EXECUTOR_CLASS_NAME = "java.util.concurrent.ScheduledThreadPoolExecutor";
  private static final String SET_CORE_POOL_SIZE_METHOD_NAME = "setCorePoolSize";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitNewExpression(@NotNull PsiNewExpression expression) {
        super.visitNewExpression(expression);
        final PsiExpression arg = getZeroArgument(expression.getArgumentList());
        if (arg == null) return;
        final PsiType type = expression.getType();
        if (!TypeUtils.typeEquals(SCHEDULED_THREAD_POOL_EXECUTOR_CLASS_NAME, type)) return;
        holder.registerProblem(arg,
                               JavaBundle.message("scheduled.thread.pool.executor.with.zero.core.threads.description"),
                               ProblemHighlightType.WARNING);
      }

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final String methodName = expression.getMethodExpression().getReferenceName();
        if (methodName == null || !methodName.equals(SET_CORE_POOL_SIZE_METHOD_NAME)) return;
        final PsiExpression arg = getZeroArgument(expression.getArgumentList());
        if (arg == null) return;
        final PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        final TypeConstraint constraint = TypeConstraint.fromDfType(CommonDataflow.getDfType(qualifier));
        final PsiType type = constraint.getPsiType(expression.getProject());
        if (!TypeUtils.typeEquals(SCHEDULED_THREAD_POOL_EXECUTOR_CLASS_NAME, type)) return;
        holder.registerProblem(arg,
                               JavaBundle.message("scheduled.thread.pool.executor.with.zero.core.threads.description"),
                               ProblemHighlightType.WARNING);
      }
    };
  }

  @Nullable
  private static PsiExpression getZeroArgument(@Nullable PsiExpressionList argList) {
    if (argList == null) return null;
    final PsiExpression[] args = argList.getExpressions();
    if (args.length != 1) return null;
    final PsiExpression arg = args[0];
    final PsiType argType = arg.getType();
    if (argType == null || !argType.equals(PsiTypes.intType())) return null;
    final Integer value = ObjectUtils.tryCast(CommonDataflow.computeValue(arg), Integer.class);
    return value != null && value.intValue() == 0 ? arg : null;
  }
}
