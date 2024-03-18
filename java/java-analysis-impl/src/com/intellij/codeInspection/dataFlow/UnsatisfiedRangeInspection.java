// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public final class UnsatisfiedRangeInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) return;
        PsiType returnType = method.getReturnType();
        LongRangeSet fromType = JvmPsiRangeSetUtil.typeRange(returnType);
        if (fromType == null) return;
        LongRangeSet range = JvmPsiRangeSetUtil.fromPsiElement(method);
        if (range.contains(fromType)) return;
        PsiReturnStatement[] statements = PsiUtil.findReturnStatements(body);
        for (PsiReturnStatement statement : statements) {
          PsiExpression value = statement.getReturnValue();
          if (value == null) continue;
          ExpressionUtils.nonStructuralChildren(value).forEach(expression -> {
            DfIntegralType val = ObjectUtils.tryCast(CommonDataflow.getDfType(expression), DfIntegralType.class);
            if (val != null) {
              LongRangeSet returnRange = val.getRange();
              if (!returnRange.intersects(range)) {
                Long constantValue = returnRange.getConstantValue();
                String message = JavaAnalysisBundle
                  .message(constantValue == null ? "inspection.unsatisfied.range.message" : "inspection.unsatisfied.range.message.value",
                           JvmPsiRangeSetUtil.getPresentationText(returnRange, returnType), JvmPsiRangeSetUtil
                             .getPresentationText(range, returnType));
                holder.registerProblem(expression, message);
              }
            }
          });
        }
      }
    };
  }
}
