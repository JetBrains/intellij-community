/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.trivialif;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class MergeParallelIfsIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("merge.parallel.ifs.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("merge.parallel.ifs.intention.name");
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new MergeParallelIfsPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiIfStatement firstStatement = (PsiIfStatement)token.getParent();
    final PsiIfStatement secondStatement = (PsiIfStatement)PsiTreeUtil.skipWhitespacesForward(firstStatement);
    assert secondStatement != null;
    final CommentTracker tracker = new CommentTracker();
    final String newStatementText = mergeIfStatements(firstStatement, secondStatement, tracker);
    PsiReplacementUtil.replaceStatement(firstStatement, newStatementText, tracker);
    secondStatement.delete();
  }

  private static String mergeIfStatements(PsiIfStatement firstStatement,
                                          PsiIfStatement secondStatement,
                                          CommentTracker tracker) {
    final PsiExpression condition = firstStatement.getCondition();
    final String conditionText = condition == null ? "" : tracker.text(condition);
    final PsiStatement firstThenBranch = firstStatement.getThenBranch();
    final PsiStatement secondThenBranch = secondStatement.getThenBranch();
    @NonNls String statement = "if(" + conditionText + ')' + printStatementsInSequence(firstThenBranch, secondThenBranch, tracker);
    final PsiStatement firstElseBranch = firstStatement.getElseBranch();
    final PsiStatement secondElseBranch = secondStatement.getElseBranch();
    if (firstElseBranch != null || secondElseBranch != null) {
      if (firstElseBranch instanceof PsiIfStatement && secondElseBranch instanceof PsiIfStatement
          && MergeParallelIfsPredicate.ifStatementsCanBeMerged((PsiIfStatement)firstElseBranch, (PsiIfStatement)secondElseBranch)) {
        statement += "else " + mergeIfStatements((PsiIfStatement)firstElseBranch, (PsiIfStatement)secondElseBranch, tracker);
      }
      else {
        statement += "else" + printStatementsInSequence(firstElseBranch, secondElseBranch, tracker);
      }
    }
    return statement;
  }

  private static String printStatementsInSequence(PsiStatement statement1, PsiStatement statement2, CommentTracker tracker) {
    if (statement1 == null) {
      return ' ' + tracker.text(statement2);
    }
    if (statement2 == null) {
      return ' ' + tracker.text(statement1);
    }
    final StringBuilder out = new StringBuilder("{");
    printStatementStripped(statement1, tracker, out);
    printStatementStripped(statement2, tracker, out);
    out.append('}');
    return out.toString();
  }

  private static void printStatementStripped(PsiStatement statement, CommentTracker tracker, StringBuilder out) {
    if (statement instanceof PsiBlockStatement) {
      final PsiCodeBlock block = ((PsiBlockStatement)statement).getCodeBlock();
      final PsiElement[] children = block.getChildren();
      for (int i = 1; i < children.length - 1; i++) {
        out.append(tracker.text(children[i]));
      }
    }
    else {
      out.append(tracker.text(statement));
    }
  }
}