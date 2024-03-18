// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.ExtractParameterAsLocalVariableFix;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class AssignmentToMethodParameterInspection extends BaseAssignmentToParameterInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ExtractParameterAsLocalVariableFix();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.method.parameter.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreTransformationOfOriginalParameter", InspectionGadgetsBundle.message(
        "assignment.to.method.parameter.ignore.transformation.option")));
  }

  @Override
  protected boolean isApplicable(PsiParameter parameter) {
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) {
      return false;
    }
    return !JavaPsiRecordUtil.isCompactConstructor((PsiMethod)scope);
  }
}
