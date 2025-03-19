// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class DeleteCatchSectionFix extends PsiUpdateModCommandQuickFix {

  private final boolean removeTryCatch;

  public DeleteCatchSectionFix(boolean removeTryCatch) {
    this.removeTryCatch = removeTryCatch;
  }

  @Override
  public @NotNull String getName() {
    if (removeTryCatch) {
      return CommonQuickFixBundle.message("fix.remove.statement", "try-catch");
    }
    else {
      return InspectionGadgetsBundle.message("delete.catch.section.quickfix");
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("delete.catch.section.fix.family.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiParameter parameter)) {
      return;
    }
    final PsiElement grandParent = parameter.getParent();
    if (!(grandParent instanceof PsiCatchSection catchSection)) {
      return;
    }
    if (removeTryCatch) {
      BlockUtils.unwrapTryBlock(catchSection.getTryStatement());
    }
    else {
      catchSection.delete();
    }
  }
}
