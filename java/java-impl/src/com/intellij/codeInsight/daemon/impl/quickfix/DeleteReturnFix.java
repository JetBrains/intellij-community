// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class DeleteReturnFix extends PsiUpdateModCommandAction<PsiReturnStatement> {
  private final boolean myIsLastStatement;
  private final boolean myHasSideEffects;

  public DeleteReturnFix(@NotNull PsiMethod method, @NotNull PsiReturnStatement returnStatement) {
    super(returnStatement);
    PsiCodeBlock codeBlock = Objects.requireNonNull(method.getBody());
    myIsLastStatement = ControlFlowUtils.blockCompletesWithStatement(codeBlock, returnStatement);
    myHasSideEffects = SideEffectChecker.mayHaveSideEffects(Objects.requireNonNull(returnStatement.getReturnValue()));
  }

  @Override
  protected Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReturnStatement element) {
    String toDelete = myIsLastStatement ? "statement" : "value";
    String message = QuickFixBundle.message(myHasSideEffects ? "delete.return.fix.side.effects.text" : "delete.return.fix.text", toDelete);
    return Presentation.of(message);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("delete.return.fix.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReturnStatement returnStatement, @NotNull ModPsiUpdater updater) {
    PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) return;
    CommentTracker ct = new CommentTracker();
    if (myHasSideEffects) {
      returnValue = Objects.requireNonNull(CodeBlockSurrounder.forExpression(returnValue)).surround().getExpression();
      returnStatement = (PsiReturnStatement)returnValue.getParent();
    }
    List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(returnValue);
    sideEffects.forEach(ct::markUnchanged);
    PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, returnValue);
    if (statements.length > 0) BlockUtils.addBefore(returnStatement, statements);
    PsiElement toDelete = myIsLastStatement ? returnStatement : returnValue;
    ct.deleteAndRestoreComments(toDelete);
  }
}
