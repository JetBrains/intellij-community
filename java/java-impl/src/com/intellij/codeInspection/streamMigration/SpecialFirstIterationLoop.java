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
package com.intellij.codeInspection.streamMigration;


import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.psiutils.ExpressionUtils.resolveLocalVariable;

class SpecialFirstIterationLoop {
  private final @NotNull List<PsiStatement> myFirstIterationStatements;
  private final @NotNull List<PsiStatement> myOtherIterationStatements;
  private final @Nullable PsiLocalVariable myVariable;

  public SpecialFirstIterationLoop(@NotNull List<PsiStatement> firstIterationStatements,
                                   @NotNull List<PsiStatement> otherIterationStatements,
                                   @Nullable PsiLocalVariable variable) {
    myFirstIterationStatements = firstIterationStatements;
    myOtherIterationStatements = otherIterationStatements;
    myVariable = variable;
  }


  @NotNull
  public List<PsiStatement> getOtherIterationStatements() {
    return myOtherIterationStatements;
  }

  @NotNull
  public List<PsiStatement> getFirstIterationStatements() {
    return myFirstIterationStatements;
  }

  @Nullable
  public PsiLocalVariable getVariable() {
    return myVariable;
  }

  @Nullable
  private static PsiExpression getExpressionComparedEqWithZero(@NotNull PsiBinaryExpression binaryExpression) {
    if (!binaryExpression.getOperationTokenType().equals(JavaTokenType.EQEQ)) return null;
    PsiExpression rOperand = binaryExpression.getROperand();
    if (rOperand == null) return null;
    PsiExpression lOperand = binaryExpression.getLOperand();
    if (ExpressionUtils.isZero(lOperand)) return rOperand;
    if (ExpressionUtils.isZero(rOperand)) return lOperand;
    return null;
  }

  @Contract("null -> null")
  @Nullable
  static PsiExpression getExpressionComparedToZero(@Nullable PsiBinaryExpression condition) {
    if (condition == null) return null;
    IElementType tokenType = condition.getOperationTokenType();
    PsiExpression left = condition.getLOperand();
    PsiExpression right = condition.getROperand();
    if (ExpressionUtils.isZero(right)) {
      if (tokenType.equals(JavaTokenType.NE) || tokenType.equals(JavaTokenType.GT)) return left;
    }
    else if (ExpressionUtils.isZero(left)) {
      if (tokenType.equals(JavaTokenType.NE) || tokenType.equals(JavaTokenType.LT)) return right;
    }
    return null;
  }

  @Nullable
  private static SpecialFirstIterationLoop extract(boolean firstIterationThen,
                                                   int index,
                                                   @NotNull List<PsiStatement> statements,
                                                   @NotNull PsiLocalVariable checkVar) {
    PsiStatement statement = statements.get(index);
    PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
    if (ifStatement == null) return null;
    List<PsiStatement> thenStatements = unwrapBlock(ifStatement.getThenBranch());
    List<PsiStatement> elseStatements = unwrapBlock(ifStatement.getElseBranch());
    return extract(firstIterationThen, index, thenStatements, elseStatements, statements, checkVar);
  }

  @Nullable
  private static SpecialFirstIterationLoop extract(boolean firstIterationThen,
                                                   int index,
                                                   @NotNull List<PsiStatement> thenStatements,
                                                   @NotNull List<PsiStatement> elseStatements,
                                                   @NotNull List<PsiStatement> statements,
                                                   @NotNull PsiLocalVariable checkVar) {
    PsiStatement statement = statements.get(index);
    PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
    if (ifStatement == null) return null;
    List<PsiStatement> beforeStatements = statements.subList(0, index);
    List<PsiStatement> afterStatements = statements.subList(index + 1, statements.size());


    ArrayList<PsiStatement> firstIteration = new ArrayList<>(beforeStatements);
    ArrayList<PsiStatement> otherIterations = new ArrayList<>(beforeStatements);

    firstIteration.addAll(firstIterationThen ? thenStatements : elseStatements);
    firstIteration.addAll(afterStatements);

    otherIterations.addAll(firstIterationThen ? elseStatements : thenStatements);
    otherIterations.addAll(afterStatements);

    return new SpecialFirstIterationLoop(firstIteration, otherIterations, checkVar);
  }

  /**
   * @return index of PsiStatement if it is the only statement, matches predicate or -1 otherwise
   */
  private static int getSingleStatementIndex(@NotNull List<PsiStatement> statements, @NotNull Predicate<PsiStatement> predicate) {
    int index = -1;
    for (int i = 0; i < statements.size(); i++) {
      PsiStatement statement = statements.get(i);
      if (!predicate.test(statement)) continue;
      if (index != -1) return -1;
      index = i;
    }
    return index;
  }

  private static int getSingleAssignmentIndex(@NotNull List<PsiStatement> statements) {
    return getSingleStatementIndex(statements, statement -> ExpressionUtils.getAssignment(statement) != null);
  }


  static class BoolFlagLoop {
    private BoolFlagLoop(){}
    /*
    Cases:
    if(first) { ... } else { ... }
    if(!first) ...
    if(notFirst) ...
     */
    @Nullable
    static SpecialFirstIterationLoop extract(TerminalBlock terminalBlock) {
      ArrayList<PsiStatement> statements = ContainerUtil.newArrayList(terminalBlock.getStatements());
      int index = getSingleStatementIndex(statements, PsiIfStatement.class::isInstance);
      if (index == -1) return null;
      PsiStatement statement = statements.get(index);
      PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
      if (ifStatement == null) return null;
      ThreeState firstIterationThen = isFirstIterationThen(statement);
      if (firstIterationThen.equals(ThreeState.UNSURE)) return null;
      final ConditionData conditionData = ConditionData.extract(ifStatement, firstIterationThen.toBoolean());
      if(conditionData == null) return null;

      PsiAssignmentExpression assignment = conditionData.getAssignment();
      PsiExpression expression = assignment.getLExpression();
      PsiLocalVariable boolFlag = resolveLocalVariable(expression);
      if(boolFlag == null) return null;
      PsiExpression rExpression = assignment.getRExpression();
      if (rExpression == null) return null;
      if (!assignmentNegatesInitializer(boolFlag, rExpression)) return null;
      PsiExpression condition = ifStatement.getCondition();
      boolean referencesAllowed =
        ReferencesSearch.search(boolFlag).forEach(reference -> PsiTreeUtil.isAncestor(condition, reference.getElement(), false) ||
                                                               PsiTreeUtil.isAncestor(assignment, reference.getElement(), false) ||
                                                               PsiTreeUtil.isAncestor(boolFlag, reference.getElement(), false));
      if (!referencesAllowed) return null;
      return SpecialFirstIterationLoop
        .extract(firstIterationThen.toBoolean(), index, conditionData.getThenStatements(), conditionData.getElseStatements(), statements,
                 boolFlag);
    }

    private static boolean assignmentNegatesInitializer(@NotNull PsiVariable boolFlag, @NotNull PsiExpression expression) {
      Object constantExpression = ExpressionUtils.computeConstantExpression(expression);
      if (!(constantExpression instanceof Boolean)) return false;
      boolean assignmentValue = (boolean)constantExpression;
      return ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(boolFlag.getInitializer()), !assignmentValue);
    }

    @NotNull
    private static ThreeState isFirstIterationThen(@NotNull PsiStatement statement) {
      PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
      if (ifStatement == null) return ThreeState.UNSURE;
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return ThreeState.UNSURE;

      boolean negated = BoolUtils.isNegation(condition);
      condition = PsiUtil.skipParenthesizedExprDown(condition);
      PsiExpression expression = negated ? BoolUtils.getNegated(condition) : condition;
      PsiLocalVariable boolFlagVar = resolveLocalVariable(expression);
      if(boolFlagVar == null) return ThreeState.UNSURE;
      return ThreeState.fromBoolean(ExpressionUtils.isLiteral(PsiUtil.skipParenthesizedExprDown(boolFlagVar.getInitializer()), !negated));
    }


    private static class ConditionData {
      private final @NotNull List<PsiStatement> myThenStatements;
      private final @NotNull List<PsiStatement> myElseStatements;
      private final @NotNull PsiAssignmentExpression myAssignment;

      private ConditionData(@NotNull List<PsiStatement> thenStatements,
                            @NotNull List<PsiStatement> elseStatements,
                            @NotNull PsiAssignmentExpression assignment) {
        myThenStatements = thenStatements;
        myElseStatements = elseStatements;
        myAssignment = assignment;
      }

      @NotNull
      public PsiAssignmentExpression getAssignment() {
        return myAssignment;
      }

      @NotNull
      public List<PsiStatement> getElseStatements() {
        return myElseStatements;
      }

      @NotNull
      public List<PsiStatement> getThenStatements() {
        return myThenStatements;
      }


      @Nullable
      static ConditionData extract(@NotNull PsiIfStatement ifStatement, boolean firstIterationThen) {
        PsiStatement block = firstIterationThen ? ifStatement.getThenBranch() : ifStatement.getElseBranch();
        ArrayList<PsiStatement> firstIterationStatements = new ArrayList<>(unwrapBlock(block));
        int index = getSingleAssignmentIndex(firstIterationStatements);
        if (index == -1) return null;
        PsiStatement assignment = firstIterationStatements.remove(index);
        PsiExpressionStatement expressionStatement = tryCast(assignment, PsiExpressionStatement.class);
        if(expressionStatement == null) return null;
        PsiAssignmentExpression assignmentExpression = tryCast(expressionStatement.getExpression(), PsiAssignmentExpression.class);
        if(assignmentExpression == null) return null;
        PsiStatement otherBlock = firstIterationThen ? ifStatement.getElseBranch() : ifStatement.getThenBranch();
        List<PsiStatement> otherIterationStatements = unwrapBlock(otherBlock);
        return firstIterationThen
               ? new ConditionData(firstIterationStatements, otherIterationStatements, assignmentExpression)
               : new ConditionData(otherIterationStatements, firstIterationStatements, assignmentExpression);
      }
    }
  }

  static class IndexBasedLoop{
    private IndexBasedLoop(){}

    /*
  if(i == 0) {
      sb.append(mainPart);
  } else {
      sb.append(",").append(mainPart);
  }

  if(i > 0) {
    sb.append(",");
  }
  sb.append(mainPart)

  if(i != 0) {
    sb.append(",");
  }
  sb.append(mainPart)
 */
    @Nullable
    static SpecialFirstIterationLoop extract(@NotNull TerminalBlock terminalBlock) {
      StreamApiMigrationInspection.CountingLoopSource countingLoopSource =
        terminalBlock.getLastOperation(StreamApiMigrationInspection.CountingLoopSource.class);
      if (countingLoopSource == null) return null;
      PsiVariable loopVar = countingLoopSource.getVariable();
      PsiLocalVariable loopLocalVar = tryCast(loopVar, PsiLocalVariable.class);
      if (loopLocalVar == null) return null;

      ArrayList<PsiStatement> statements = ContainerUtil.newArrayList(terminalBlock.getStatements());
      int index = getSingleStatementIndex(statements, statement -> statement instanceof PsiIfStatement);
      if (index == -1) return null;
      ThreeState firstIterationThen = isFirstIterationThen(statements.get(index), loopVar);
      if (firstIterationThen.equals(ThreeState.UNSURE)) return null;
      return SpecialFirstIterationLoop.extract(firstIterationThen.toBoolean(), index, statements, loopLocalVar);
    }

    @NotNull
    private static ThreeState isFirstIterationThen(@NotNull PsiStatement statement, @NotNull PsiVariable loopVar) {
      PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
      if (ifStatement == null) return ThreeState.UNSURE;
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return ThreeState.UNSURE;
      PsiBinaryExpression binaryExpression = tryCast(condition, PsiBinaryExpression.class);
      if (binaryExpression == null) return ThreeState.UNSURE;

      PsiExpression comparedEqWithZero = getExpressionComparedEqWithZero(binaryExpression);
      if (comparedEqWithZero != null) {
        if (!ExpressionUtils.isReferenceTo(comparedEqWithZero, loopVar)) return ThreeState.UNSURE;
        return ThreeState.YES;
      }
      PsiExpression notEqWithZero = getExpressionComparedToZero(binaryExpression);
      if (notEqWithZero == null || !ExpressionUtils.isReferenceTo(notEqWithZero, loopVar)) return ThreeState.UNSURE;
      return ThreeState.NO;
    }
  }

  @NotNull
  private static List<PsiStatement> unwrapBlock(@Nullable PsiStatement statement) {
    if(statement == null) return Collections.emptyList();
    PsiBlockStatement blockStatement = tryCast(statement, PsiBlockStatement.class);
    if(blockStatement == null) return Collections.singletonList(statement);
    return ContainerUtil.newArrayList(blockStatement.getCodeBlock().getStatements());
  }
}