// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSwitchLabelStatementBase;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.CountingLoop;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class SuspiciousListRemoveInLoopInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher LIST_REMOVE = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "remove").parameterTypes("int");

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!LIST_REMOVE.test(call)) return;
        PsiReferenceExpression arg =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiReferenceExpression.class);
        if (arg == null) return;
        PsiExpressionStatement parentStatement = ObjectUtils.tryCast(call.getParent(), PsiExpressionStatement.class);
        if (parentStatement == null) return;
        PsiElement parent = parentStatement.getParent();
        while (parent instanceof PsiLabeledStatement ||
               parent instanceof PsiIfStatement ||
               parent instanceof PsiSwitchLabelStatementBase ||
               parent instanceof PsiSwitchStatement ||
               parent instanceof PsiBlockStatement ||
               parent instanceof PsiCodeBlock) {
          parent = parent.getParent();
        }

        if (!(parent instanceof PsiForStatement)) return;
        CountingLoop loop = CountingLoop.from((PsiForStatement)parent);
        if (loop == null || loop.isDescending()) return;
        if (!arg.isReferenceTo(loop.getCounter())) return;
        if (ControlFlowUtils.isExecutedOnceInLoop(parentStatement, (PsiLoopStatement)parent)) return;
        holder.registerProblem(Objects.requireNonNull(call.getMethodExpression().getReferenceNameElement()),
                               JavaBundle.message("inspection.suspicious.list.remove.display.name"));
      }
    };
  }
}
