// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class BaseStreamApiMigration {
  private boolean myShouldWarn;
  private final String myReplacement;

  protected BaseStreamApiMigration(boolean shouldWarn, String replacement) {
    myShouldWarn = shouldWarn;
    myReplacement = replacement;
  }

  public String getReplacement() {
    return myReplacement;
  }

  abstract PsiElement migrate(@NotNull Project project, @NotNull PsiElement body, @NotNull TerminalBlock tb);

  public boolean isShouldWarn() {
    return myShouldWarn;
  }

  public void setShouldWarn(boolean shouldWarn) {
    myShouldWarn = shouldWarn;
  }

  static PsiElement replaceWithOperation(PsiStatement loopStatement,
                                         PsiVariable var,
                                         String streamText,
                                         PsiType expressionType,
                                         OperationReductionMigration.ReductionOperation reductionOperation,
                                         CommentTracker ct) {
    InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(var, loopStatement);
    if (status != InitializerUsageStatus.UNKNOWN) {
      PsiExpression initializer = var.getInitializer();
      if (initializer != null && reductionOperation.getInitializerExpressionRestriction().test(initializer)) {
        PsiType type = var.getType();
        String replacement = (type.isAssignableFrom(expressionType) ? "" : "(" + type.getCanonicalText() + ") ") + streamText;
        return replaceInitializer(loopStatement, var, initializer, replacement, status, ct);
      }
    }
    return ct.replaceAndRestoreComments(loopStatement, var.getName() + reductionOperation.getOperation() + "=" + streamText + ";");
  }

  static PsiElement replaceInitializer(PsiStatement loopStatement,
                                       PsiVariable var,
                                       PsiExpression initializer,
                                       String replacement,
                                       InitializerUsageStatus status,
                                       CommentTracker ct) {
    if (status == ControlFlowUtils.InitializerUsageStatus.DECLARED_JUST_BEFORE) {
      ct.replace(initializer, replacement);
      removeLoop(ct, loopStatement);
      return var;
    }
    else {
      if (status == ControlFlowUtils.InitializerUsageStatus.AT_WANTED_PLACE_ONLY) {
        PsiTypeElement typeElement = var.getTypeElement();
        if (typeElement == null || !typeElement.isInferredType() || PsiTypesUtil.replaceWithExplicitType(typeElement) != null) {
          ct.delete(initializer);
        }
      }
      return ct.replaceAndRestoreComments(loopStatement, var.getName() + " = " + replacement + ";");
    }
  }


  static @Nullable PsiElement replaceWithFindExtremum(@NotNull CommentTracker ct, @NotNull PsiStatement loopStatement,
                                                      @NotNull PsiVariable extremumHolder,
                                                      @NotNull String streamText,
                                                      @Nullable PsiVariable keyExtremum) {
    if(keyExtremum != null) {
      ct.delete(keyExtremum);
    }
    InitializerUsageStatus status = ControlFlowUtils.getInitializerUsageStatus(extremumHolder, loopStatement);
    return replaceInitializer(loopStatement, extremumHolder, extremumHolder.getInitializer(), streamText, status, ct);
  }

  static void removeLoop(CommentTracker ct, @NotNull PsiStatement statement) {
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiLabeledStatement) {
      ct.deleteAndRestoreComments(parent);
    }
    else {
      ct.deleteAndRestoreComments(statement);
    }
  }
}
