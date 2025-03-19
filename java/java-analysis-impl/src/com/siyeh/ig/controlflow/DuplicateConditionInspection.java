/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.NavigateToDuplicateExpressionFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class DuplicateConditionInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreSideEffectConditions = true;

  // This is a dirty fix of 'squared' algorithm performance issue.
  private static final int LIMIT_DEPTH = 20;

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("duplicate.condition.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSideEffectConditions", InspectionGadgetsBundle.message("duplicate.condition.ignore.method.calls.option"))
        .description(InspectionGadgetsBundle.message("duplicate.condition.ignore.method.calls.option.description")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DuplicateConditionVisitor();
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    if (ArrayUtil.getFirstElement(infos) instanceof PsiExpression duplicate) {
      return new NavigateToDuplicateExpressionFix(duplicate);
    }
    return null;
  }

  private class DuplicateConditionVisitor extends BaseInspectionVisitor {
    private final Set<PsiIfStatement> myAnalyzedAndStatements = new HashSet<>();
    private final Set<PsiIfStatement> myAnalyzedOrStatements = new HashSet<>();

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);

      if (ControlFlowUtils.isElseIf(statement)) return;
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) return;
      if (parent instanceof PsiCodeBlock codeBlock && ArrayUtil.getFirstElement(codeBlock.getStatements()) == statement && 
          parent.getParent() instanceof PsiBlockStatement blockStatement && blockStatement.getParent() instanceof PsiIfStatement parentIf &&
          parentIf.getThenBranch() == blockStatement) {
        return;
      }

      final Set<PsiExpression> conditions = new LinkedHashSet<>();
      collectConditionsForIfStatementOrChain(statement, conditions, 0);
      if (conditions.size() >= 2) {
        findDuplicatesAccordingToSideEffects(conditions);
      }
      conditions.clear();
      collectConditionsForIfStatementAndChain(statement, conditions, 0);
      if (conditions.size() >= 2) {
        findDuplicatesAccordingToSideEffects(conditions);
      }
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);

      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ANDAND) && !tokenType.equals(JavaTokenType.OROR)) return;

      PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
      if (parent instanceof PsiIfStatement) return;
      if (parent instanceof PsiBinaryExpression parentExpression) {
        if (tokenType.equals(parentExpression.getOperationTokenType())) return;
      }

      final Set<PsiExpression> conditions = new LinkedHashSet<>();
      collectConditionsForExpression(expression, conditions, tokenType);
      if (conditions.size() < 2) return;

      findDuplicatesAccordingToSideEffects(conditions);
    }

    private void collectConditionsForIfStatementAndChain(PsiIfStatement statement, Set<? super PsiExpression> conditions, int depth) {
      if (depth > LIMIT_DEPTH || !myAnalyzedAndStatements.add(statement)) return;
      final PsiExpression condition = statement.getCondition();
      collectConditionsForExpression(condition, conditions, JavaTokenType.ANDAND);
      final PsiStatement branch = ControlFlowUtils.stripBraces(statement.getThenBranch());
      if (branch instanceof PsiIfStatement ifStatement) {
        collectConditionsForIfStatementAndChain(ifStatement, conditions, depth + 1);
      }
      if (branch instanceof PsiBlockStatement blockStatement) {
        PsiStatement[] statements = blockStatement.getCodeBlock().getStatements();
        if (statements.length == 0) return;
        if (statements[0] instanceof PsiIfStatement ifStatement) {
          collectConditionsForIfStatementAndChain(ifStatement, conditions, depth + 1);
        }
      }
    }
    
    private void collectConditionsForIfStatementOrChain(PsiIfStatement statement, Set<? super PsiExpression> conditions, int depth) {
      if (depth > LIMIT_DEPTH || !myAnalyzedOrStatements.add(statement)) return;
      final PsiExpression condition = statement.getCondition();
      collectConditionsForExpression(condition, conditions, JavaTokenType.OROR);
      final PsiStatement branch = ControlFlowUtils.stripBraces(statement.getElseBranch());
      if (branch instanceof PsiIfStatement) {
        collectConditionsForIfStatementOrChain((PsiIfStatement)branch, conditions, depth + 1);
      }
      if (branch == null) {
        final PsiStatement thenBranch = statement.getThenBranch();
        if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) return;
        PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(statement);
        if (next instanceof PsiIfStatement) {
          collectConditionsForIfStatementOrChain((PsiIfStatement)next, conditions, depth + 1);
        }
      }
    }

    private static void collectConditionsForExpression(PsiExpression condition,
                                                       Set<? super PsiExpression> conditions,
                                                       IElementType wantedTokenType) {
      condition = PsiUtil.skipParenthesizedExprDown(condition);
      if (condition == null) return;
      if (condition instanceof PsiPolyadicExpression polyadicExpression) {
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (wantedTokenType.equals(tokenType)) {
          final PsiExpression[] operands = polyadicExpression.getOperands();
          for (PsiExpression operand : operands) {
            collectConditionsForExpression(operand, conditions, wantedTokenType);
          }
          return;
        }
      }
      while (true) {
        PsiExpression negated = BoolUtils.getNegated(condition);
        if (negated == null) break;
        condition = negated;
      }
      conditions.add(condition);
    }

    private void findDuplicatesAccordingToSideEffects(Set<PsiExpression> conditions) {
      final List<PsiExpression> conditionList = new ArrayList<>(conditions);
      ThreeState wantedStatus = ignoreSideEffectConditions ? ThreeState.UNSURE : ThreeState.YES;
      conditionList.replaceAll(cond -> SideEffectChecker.getSideEffectStatus(cond).isAtLeast(wantedStatus) ? null : cond);
      // Every condition having side-effect separates non-side-effect conditions into independent groups
      // like:
      // if(!readToken() || token == X || token == Y) ...
      // else if(!readToken() || token == X || token == Y) ...
      // here we analyze independently first ['token == X', 'token == Y'] and second ['token == X', 'token == Y']
      // thus no warning is issued. Such constructs often appear in parsers.
      StreamEx.of(conditionList).groupRuns((a, b) -> a != null && b != null)
        .filter(list -> list.size() >= 2).forEach(this::findDuplicates);
    }

    private void findDuplicates(List<PsiExpression> conditions) {
      final BitSet matched = new BitSet();
      for (int i = 0; i < conditions.size(); i++) {
        if (matched.get(i)) continue;
        final PsiExpression condition = conditions.get(i);
        for (int j = i + 1; j < conditions.size(); j++) {
          if (matched.get(j)) continue;
          final PsiExpression testCondition = conditions.get(j);
          final boolean areEquivalent = EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(condition, testCondition);
          if (areEquivalent) {
            registerError(testCondition, condition);
            if (!matched.get(i)) {
              registerError(condition, testCondition);
            }
            matched.set(i);
            matched.set(j);
          }
        }
      }
    }
  }
}
