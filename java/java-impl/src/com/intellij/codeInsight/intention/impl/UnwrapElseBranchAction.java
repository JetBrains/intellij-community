// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class UnwrapElseBranchAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(UnwrapElseBranchAction.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiIfStatement ifStatement) {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      PsiElement grandParent = ifStatement.getParent();
      if (elseBranch != null && grandParent != null) {
        if (!(grandParent instanceof PsiCodeBlock)) {
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
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
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiKeyword keyword && keyword.getTokenType() == JavaTokenType.ELSE_KEYWORD &&
        element.getParent() instanceof PsiIfStatement ifStatement) {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch != null) {
        PsiStatement thenBranch = ifStatement.getThenBranch();
        boolean thenCompletesNormally = ControlFlowUtils.statementMayCompleteNormally(thenBranch);
        boolean elseCompletesNormally = ControlFlowUtils.statementMayCompleteNormally(elseBranch);
        if (thenCompletesNormally && (elseCompletesNormally || !nextStatementMayBecomeUnreachable(ifStatement))) {
          setText(JavaBundle.message("intention.unwrap.else.branch.changes.semantics"));
          return true;
        }
      }
    }
    return false;
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
