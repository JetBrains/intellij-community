// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.streamMigration.OperationReductionMigration.SUM_OPERATION;

final class SumMigration extends BaseStreamApiMigration {

  SumMigration(boolean shouldWarn) {
    super(shouldWarn, "sum");
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if (assignment == null) return null;
    PsiVariable var = StreamApiMigrationInspection.extractSumAccumulator(assignment);
    if (var == null) return null;

    PsiExpression addend = StreamApiMigrationInspection.extractAddend(assignment);
    if (addend == null) return null;
    PsiType type = var.getType();
    if (!(type instanceof PsiPrimitiveType) || type.equals(PsiTypes.floatType())) return null;
    if (!type.equals(PsiTypes.doubleType()) && !type.equals(PsiTypes.longType())) {
      type = PsiTypes.intType();
    }
    PsiType addendType = addend.getType();
    CommentTracker ct = new CommentTracker();
    if(addendType != null && !TypeConversionUtil.isAssignable(type, addendType)) {
      addend = JavaPsiFacade.getElementFactory(project).createExpressionFromText(
        "(" + type.getCanonicalText() + ")" + ct.text(addend, ParenthesesUtils.TYPE_CAST_PRECEDENCE), addend);
    }
    String stream = tb.add(new MapOp(addend, tb.getVariable(), type)).generate(ct)+".sum()";
    return replaceWithOperation(tb.getStreamSourceStatement(), var, stream, type, SUM_OPERATION, ct);
  }
}
