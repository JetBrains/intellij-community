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

class RemoveLeadingZeroFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("remove.leading.zero.to.make.decimal.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiLiteralExpression literal)) {
      return;
    }
    removeLeadingZeroes(literal);
  }

  static void removeLeadingZeroes(PsiLiteralExpression literal) {
    final String text = literal.getText();
    final int max = text.length() - (PsiTypes.longType().equals(literal.getType()) ? 2 : 1);
    if (max < 1) {
      return;
    }
    int index = 0;
    while (index < max && (text.charAt(index) == '0' || text.charAt(index) == '_')) {
      index++;
    }
    final String textWithoutLeadingZeros = text.substring(index);
    PsiReplacementUtil.replaceExpression(literal, textWithoutLeadingZeros);
  }
}
