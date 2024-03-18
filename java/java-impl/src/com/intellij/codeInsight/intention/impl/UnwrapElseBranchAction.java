// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UnwrapElseBranchAction extends PsiUpdateModCommandAction<PsiKeyword> {
  private static final Logger LOG = Logger.getInstance(UnwrapElseBranchAction.class);
  
  public UnwrapElseBranchAction() {
    super(PsiKeyword.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiKeyword elseKeyword, @NotNull ModPsiUpdater updater) {
    PsiElement parent = elseKeyword.getParent();
    if (parent instanceof PsiIfStatement ifStatement) {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      PsiElement grandParent = ifStatement.getParent();
      if (elseBranch != null && grandParent != null) {
        if (!(grandParent instanceof PsiCodeBlock)) {
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
          PsiBlockStatement blockStatement =
            (PsiBlockStatement)factory.createStatementFromText("{" + ifStatement.getText() + "}", ifStatement);
          blockStatement = (PsiBlockStatement)ifStatement.replace(blockStatement);
          ifStatement = (PsiIfStatement)blockStatement.getCodeBlock().getStatements()[0];
          elseBranch = ifStatement.getElseBranch();
          LOG.assertTrue(elseBranch != null);
        }
        CommentTracker ct = new CommentTracker();
        InvertIfConditionAction.addAfter(ifStatement, elseBranch, ct);
        ct.deleteAndRestoreComments(elseBranch);
      }
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiKeyword keyword) {
    if (keyword.getTokenType() == JavaTokenType.ELSE_KEYWORD && keyword.getParent() instanceof PsiIfStatement ifStatement) {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch != null) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        boolean thenCompletesNormally = ControlFlowUtils.statementMayCompleteNormally(thenBranch);
        boolean elseCompletesNormally = ControlFlowUtils.statementMayCompleteNormally(elseBranch);
        if (thenCompletesNormally && (elseCompletesNormally || !nextStatementMayBecomeUnreachable(ifStatement))) {
          return Presentation.of(JavaBundle.message("intention.unwrap.else.branch.changes.semantics"));
        }
      }
    }
    return null;
  }

  /**
   * Check if there could be new unreachable code if the statement may not complete normally
   *
   * @param statement after refactoring it may not complete normally
   * @return true if the refactoring may cause unreachable code
   */
  private static boolean nextStatementMayBecomeUnreachable(PsiStatement statement) {
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (nextStatement != null) {
      return !(nextStatement instanceof PsiSwitchLabelStatement);
    }

    PsiElement parent = statement.getParent();
    if (parent instanceof PsiIfStatement ifStatement) {
      PsiStatement thenBranch = ifStatement.getThenBranch();
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (thenBranch == statement && ControlFlowUtils.statementMayCompleteNormally(elseBranch) ||
          elseBranch == statement && ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
        return false;
      }
      return nextStatementMayBecomeUnreachable(ifStatement);
    }
    if (parent instanceof PsiLabeledStatement labeledStatement) {
      return nextStatementMayBecomeUnreachable(labeledStatement);
    }
    if (parent instanceof PsiCodeBlock) {
      PsiStatement parentStatement = ObjectUtils.tryCast(parent.getParent(), PsiStatement.class);
      if (parentStatement instanceof PsiBlockStatement ||
          parentStatement instanceof PsiSynchronizedStatement ||
          parentStatement instanceof PsiTryStatement || // TODO handle try-catch more accurately
          parentStatement instanceof PsiSwitchStatement) {
        return nextStatementMayBecomeUnreachable(parentStatement);
      }
    }
    return false;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.unwrap.else.branch");
  }
}
