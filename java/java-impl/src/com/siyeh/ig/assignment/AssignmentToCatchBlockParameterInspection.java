// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.ExtractParameterAsLocalVariableFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class AssignmentToCatchBlockParameterInspection extends BaseAssignmentToParameterInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ExtractParameterAsLocalVariableFix();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("assignment.to.catch.block.parameter.problem.descriptor");
  }

  @Override
  protected boolean isApplicable(PsiParameter parameter) {
    return parameter.getDeclarationScope() instanceof PsiCatchSection &&
           !(parameter.getType() instanceof PsiDisjunctionType);
  }
}
