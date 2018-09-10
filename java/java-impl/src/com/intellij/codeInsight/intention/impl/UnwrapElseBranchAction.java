/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class UnwrapElseBranchAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(UnwrapElseBranchAction.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)parent;
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
        InvertIfConditionAction.addAfter(ifStatement, elseBranch);
        elseBranch.delete();
      }
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiKeyword && ((PsiKeyword)element).getTokenType() == JavaTokenType.ELSE_KEYWORD) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)parent;
        PsiStatement elseBranch = ifStatement.getElseBranch();
        if (elseBranch != null) {
          PsiStatement thenBranch = ifStatement.getThenBranch();
          boolean thenCompletesNormally = ControlFlowUtils.statementMayCompleteNormally(thenBranch);
          boolean elseCompletesNormally = ControlFlowUtils.statementMayCompleteNormally(elseBranch);
          if (!thenCompletesNormally ||
              elseCompletesNormally ||
              !nextStatementMayBecomeUnreachable(ifStatement)) {
            if (thenCompletesNormally) {
              setText(CodeInsightBundle.message("intention.unwrap.else.branch.changes.semantics"));
            }
            else {
              setText(CodeInsightBundle.message("intention.unwrap.else.branch"));
            }
            return true;
          }
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
    if (parent instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)parent;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (thenBranch == statement && ControlFlowUtils.statementMayCompleteNormally(elseBranch) ||
          elseBranch == statement && ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
        return false;
      }
      return nextStatementMayBecomeUnreachable(ifStatement);
    }
    if (parent instanceof PsiLabeledStatement) {
      return nextStatementMayBecomeUnreachable((PsiLabeledStatement)parent);
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
    return CodeInsightBundle.message("intention.unwrap.else.branch");
  }
}
