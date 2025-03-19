// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DeleteUnnecessaryStatementFix extends PsiUpdateModCommandQuickFix {

  private final String name;

  public DeleteUnnecessaryStatementFix(@NonNls String name) {
    this.name = name;
  }

  @Override
  public @NotNull String getName() {
    return InspectionGadgetsBundle.message(
      "smth.unnecessary.remove.quickfix", name);
  }

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("delete.unnecessary.statement.fix.family.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement keywordElement, @NotNull ModPsiUpdater updater) {
    final PsiStatement statement = PsiTreeUtil.getParentOfType(keywordElement, PsiStatement.class);
    if (statement == null) {
      return;
    }
    deleteUnnecessaryStatement(statement);
  }

  public static void deleteUnnecessaryStatement(PsiStatement statement) {
    CommentTracker ct = new CommentTracker();
    final PsiElement parent = statement.getParent();
    if (parent instanceof PsiIfStatement ||
        parent instanceof PsiWhileStatement ||
        parent instanceof PsiDoWhileStatement ||
        parent instanceof PsiForeachStatement ||
        parent instanceof PsiForStatement) {
      ct.replaceAndRestoreComments(statement, "{}");
    }
    else {
      ct.deleteAndRestoreComments(statement);
    }
  }
}