// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.DfaOptionalSupport;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class OptionalGetWithoutIsPresentInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        String methodName = nameElement.getText();
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
        if (qualifier == null) return;
        PsiClass optionalClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());
        if (optionalClass == null) return;
        if (DfaOptionalSupport.isOptionalGetMethodName(methodName) &&
            call.getArgumentList().isEmpty() &&
            TypeUtils.isOptional(optionalClass)) {
          CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(qualifier);
          if (result != null &&
              result.expressionWasAnalyzed(qualifier) &&
              result.getExpressionFact(qualifier, DfaFactType.OPTIONAL_PRESENCE) == null &&
              !isPresentCallWithSameQualifierExists(qualifier)) {
            holder.registerProblem(nameElement,
                                   InspectionsBundle.message("inspection.optional.get.without.is.present.message", optionalClass.getName()));
          }
        }
      }

      public boolean isPresentCallWithSameQualifierExists(PsiExpression qualifier) {
        // Conservatively skip the results of method calls if there's an isPresent() call with the same qualifier in the method
        if (qualifier instanceof PsiMethodCallExpression) {
          PsiElement context = PsiTreeUtil.getParentOfType(qualifier, PsiMember.class, PsiLambdaExpression.class);
          if (context != null) {
            return !PsiTreeUtil.processElements(context, e -> {
              if (e == qualifier || !(e instanceof PsiMethodCallExpression)) return true;
              PsiMethodCallExpression call = (PsiMethodCallExpression)e;
              if (!"isPresent".equals(call.getMethodExpression().getReferenceName()) || !call.getArgumentList().isEmpty()) return true;
              PsiExpression isPresentQualifier = call.getMethodExpression().getQualifierExpression();
              return isPresentQualifier == null || !PsiEquivalenceUtil.areElementsEquivalent(qualifier, isPresentQualifier);
            });
          }
        }
        return false;
      }
    };
  }
}
