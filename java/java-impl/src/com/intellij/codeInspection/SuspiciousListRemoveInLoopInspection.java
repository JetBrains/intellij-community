// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.CountingLoop;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class SuspiciousListRemoveInLoopInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher LIST_REMOVE = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "remove").parameterTypes("int");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        if (!LIST_REMOVE.test(call)) return;
        PsiReferenceExpression arg =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiReferenceExpression.class);
        if (arg == null) return;
        PsiExpressionStatement parentStatement = ObjectUtils.tryCast(call.getParent(), PsiExpressionStatement.class);
        if (parentStatement == null) return;
        PsiElement parent = parentStatement.getParent();
        while (parent instanceof PsiLabeledStatement ||
               parent instanceof PsiIfStatement ||
               parent instanceof PsiSwitchLabelStatement ||
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
                               InspectionsBundle.message("inspection.suspicious.list.remove.display.name"));
      }
    };
  }
}
