// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTIONS;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class RedundantUnmodifiableInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher COLLECTIONS_UNMODIFIABLE =
    staticCall(JAVA_UTIL_COLLECTIONS,"unmodifiableCollection", "unmodifiableList",
               "unmodifiableSet", "unmodifiableMap", "unmodifiableSortedSet", "unmodifiableSortedMap",
               "unmodifiableNavigableMap", "unmodifiableNavigableSet").parameterCount(1);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (ExpressionUtils.isVoidContext(call)) return;
        if (COLLECTIONS_UNMODIFIABLE.test(call)) {
          PsiExpression arg = call.getArgumentList().getExpressions()[0];
          DfType dfType = CommonDataflow.getDfType(arg);
          if (!Mutability.fromDfType(dfType).isUnmodifiable()) return;

          String methodName = call.getMethodExpression().getReferenceName();
          if (methodName == null) return;

          holder.registerProblem(call,
                                 JavaBundle.message("inspection.redundant.unmodifiable.call.display.name", methodName),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new TextRange(0, call.getArgumentList().getStartOffsetInParent()),
                                 new UnwrapUnmodifiableFix());
        }
      }
    };
  }

  private static class UnwrapUnmodifiableFix extends PsiUpdateModCommandQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.redundant.unmodifiable.call.unwrap.argument.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(element, PsiMethodCallExpression.class);
      if (call == null) return;

      final PsiExpressionList argList = call.getArgumentList();
      final PsiExpression[] args = argList.getExpressions();
      if (args.length != 1) return;

      CommentTracker commentTracker = new CommentTracker();
      commentTracker.replaceAndRestoreComments(element, commentTracker.text(args[0]));
    }
  }
}
