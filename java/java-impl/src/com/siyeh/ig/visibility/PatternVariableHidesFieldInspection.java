// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.visibility;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPatternVariable;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class PatternVariableHidesFieldInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("pattern.variable.hides.field.problem.descriptor", aClass.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PatternVariableHidesFieldVisitor();
  }

  private static class PatternVariableHidesFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPatternVariable(@NotNull PsiPatternVariable variable) {
      super.visitPatternVariable(variable);
      final PsiClass aClass = LocalVariableHidingMemberVariableInspection.findSurroundingClassWithHiddenField(variable, true, true);
      if (aClass == null) {
        return;
      }
      registerVariableError(variable, aClass);
    }
  }
}
