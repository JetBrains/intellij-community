// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public final class AnonymousClassVariableHidesContainingMethodVariableInspection extends
                                                                           BaseInspection {
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiParameter) {
      return InspectionGadgetsBundle.message(
        "anonymous.class.parameter.hides.containing.method.variable.problem.descriptor");
    }
    else if (info instanceof PsiField) {
      return InspectionGadgetsBundle.message(
        "anonymous.class.field.hides.containing.method.variable.problem.descriptor");
    }
    return InspectionGadgetsBundle.message(
      "anonymous.class.variable.hides.containing.method.variable.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AnonymousClassVariableHidesOuterClassVariableVisitor();
  }
}