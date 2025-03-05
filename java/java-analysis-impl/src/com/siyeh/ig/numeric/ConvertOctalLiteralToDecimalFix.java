// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiTypes;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;

class ConvertOctalLiteralToDecimalFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("convert.octal.literal.to.decimal.literal.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiLiteralExpression literalExpression)) {
      return;
    }
    replaceWithDecimalLiteral(literalExpression);
  }

  static void replaceWithDecimalLiteral(PsiLiteralExpression literalExpression) {
    final Object value = literalExpression.getValue();
    if (value == null) {
      return;
    }
    final String decimalText = value + (PsiTypes.longType().equals(literalExpression.getType()) ? "L" : "");
    PsiReplacementUtil.replaceExpression(literalExpression, decimalText);
  }
}
