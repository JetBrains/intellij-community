// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.CountingLoopSource;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.MapOp;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

final class ToArrayMigration extends BaseStreamApiMigration {
  ToArrayMigration(boolean shouldWarn) {
    super(shouldWarn, "toArray");
  }

  @Override
  PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb) {
    PsiLocalVariable arrayVariable = StreamApiMigrationInspection.extractArray(tb);
    if(arrayVariable == null) return null;
    PsiAssignmentExpression assignment = tb.getSingleExpression(PsiAssignmentExpression.class);
    if(assignment == null) return null;
    PsiExpression rValue = assignment.getRExpression();
    if(rValue == null) return null;
    PsiNewExpression initializer = tryCast(arrayVariable.getInitializer(), PsiNewExpression.class);
    if(initializer == null) return null;
    PsiExpression dimension = ArrayUtil.getFirstElement(initializer.getArrayDimensions());
    if(dimension == null) return null;
    CountingLoopSource loop = tb.getLastOperation(CountingLoopSource.class);
    if(loop == null) return null;
    PsiArrayType arrayType = tryCast(initializer.getType(), PsiArrayType.class);
    if(arrayType == null) return null;
    InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(arrayVariable, tb.getStreamSourceStatement());
    if(status == ControlFlowUtils.InitializerUsageStatus.UNKNOWN) return null;
    PsiType componentType = arrayType.getComponentType();
    String supplier;
    if(componentType instanceof PsiPrimitiveType || componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      supplier = "";
    } else {
      supplier = arrayType.getCanonicalText()+"::new";
    }
    CommentTracker ct = new CommentTracker();
    MapOp mapping = new MapOp(rValue, tb.getVariable(), assignment.getType());
    String replacementText = loop.withBound(dimension).createReplacement(ct) + mapping.createReplacement(ct) + ".toArray(" + supplier + ")";
    return replaceInitializer(tb.getStreamSourceStatement(), arrayVariable, initializer, replacementText, status, ct);
  }
}
