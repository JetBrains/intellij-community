// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.lambda;

import com.intellij.codeInsight.intention.impl.RemoveRedundantParameterTypesFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class RedundantLambdaParameterTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(RedundantLambdaParameterTypeInspection.class);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitParameterList(@NotNull PsiParameterList parameterList) {
        super.visitParameterList(parameterList);
        if (parameterList.getParent() instanceof PsiLambdaExpression &&
            RemoveRedundantParameterTypesFix.isApplicable(parameterList)) {
          for (PsiParameter parameter : parameterList.getParameters()) {
            if (parameter.getTypeElement() != null) {
              holder.problem(parameter.getTypeElement(), JavaBundle.message("inspection.message.lambda.parameter.type.is.redundant"))
                .fix(new RemoveRedundantParameterTypesFix((PsiLambdaExpression)parameterList.getParent())).register();
            }
          }
        }
      }
    };
  }
}
