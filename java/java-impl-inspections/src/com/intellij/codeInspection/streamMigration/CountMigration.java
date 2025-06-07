// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.streamMigration.OperationReductionMigration.SUM_OPERATION;

final class CountMigration extends BaseStreamApiMigration {

  CountMigration(boolean shouldWarn) {
    super(shouldWarn, "count");
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiExpression expression = tb.getSingleExpression(PsiExpression.class);
    if (expression == null) {
      expression = tb.getCountExpression();
    }
    PsiExpression operand = StreamApiMigrationInspection.extractIncrementedLValue(expression);
    if (!(operand instanceof PsiReferenceExpression)) return null;
    PsiElement element = ((PsiReferenceExpression)operand).resolve();
    if (!(element instanceof PsiLocalVariable var)) return null;
    CommentTracker ct = new CommentTracker();
    return replaceWithOperation(tb.getStreamSourceStatement(), var, tb.generate(ct) + ".count()", PsiTypes.longType(), SUM_OPERATION, ct);
  }
}
