// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.lambda;

import com.intellij.codeInsight.intention.impl.RemoveRedundantParameterTypesFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiParameterList;
import org.jetbrains.annotations.NotNull;

public class RedundantLambdaParameterTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(RedundantLambdaParameterTypeInspection.class);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitParameterList(PsiParameterList parameterList) {
        super.visitParameterList(parameterList);
        if (parameterList.getParent() instanceof PsiLambdaExpression &&
            RemoveRedundantParameterTypesFix.isApplicable(parameterList)) {
          holder.registerProblem(parameterList, JavaBundle.message("inspection.message.lambda.parameter.type.is.redundant"),
                                 new RemoveRedundantParameterTypesFix((PsiLambdaExpression)parameterList.getParent()));
        }
      }
    };
  }
}
