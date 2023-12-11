// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaMethodReferenceArgumentAnchor;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class OptionalOfNullableMisuseInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!OptionalUtil.OPTIONAL_OF_NULLABLE.matches(call)) return;
        PsiExpression arg = PsiUtil.skipParenthesizedExprDown(ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()));
        if (arg == null) return;
        DfType type = CommonDataflow.getDfType(arg);
        processArgument(type, arg);
      }

      private void processArgument(@NotNull DfType type, @NotNull PsiElement anchor) {
        DfaNullability nullability = DfaNullability.fromDfType(type);
        if (nullability == DfaNullability.NOT_NULL) {
          holder.registerProblem(anchor,
                                 JavaAnalysisBundle.message("dataflow.message.passing.non.null.argument.to.optional"),
                                 LocalQuickFix.notNullElements(DfaOptionalSupport.createReplaceOptionalOfNullableWithOfFix(anchor)));
        }
        else if (nullability == DfaNullability.NULL) {
          holder.registerProblem(anchor, JavaAnalysisBundle.message("dataflow.message.passing.null.argument.to.optional"),
                                 LocalQuickFix.notNullElements(DfaOptionalSupport.createReplaceOptionalOfNullableWithEmptyFix(anchor)));
        }
      }

      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression methodRef) {
        if (!OptionalUtil.OPTIONAL_OF_NULLABLE.methodReferenceMatches(methodRef)) return;
        CommonDataflow.DataflowResult dfr = CommonDataflow.getDataflowResult(methodRef);
        if (dfr == null) return;
        PsiElement anchor = methodRef.getReferenceNameElement();
        if (anchor == null) return;
        DfType type = dfr.getDfType(new JavaMethodReferenceArgumentAnchor(methodRef));
        processArgument(type, anchor);
      }
    };
  }
}
