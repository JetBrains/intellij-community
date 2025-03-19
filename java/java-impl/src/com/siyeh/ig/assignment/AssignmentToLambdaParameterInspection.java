// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLambdaParameterType;
import com.intellij.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.ExtractParameterAsLocalVariableFix;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class AssignmentToLambdaParameterInspection extends BaseAssignmentToParameterInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ExtractParameterAsLocalVariableFix();
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.lambda.parameter.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreTransformationOfOriginalParameter", InspectionGadgetsBundle.message(
        "assignment.to.method.parameter.ignore.transformation.option")));
  }

  @Override
  protected boolean isApplicable(PsiParameter parameter) {
    if (!(parameter.getDeclarationScope() instanceof PsiLambdaExpression)) {
      return false;
    }
    return !(parameter.getType() instanceof PsiLambdaParameterType);
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.LAMBDA_EXPRESSIONS);
  }
}
