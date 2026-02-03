/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiBreakStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEmptyStatement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionListStatement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachPatternStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResourceList;
import com.intellij.psi.PsiResourceListElement;
import com.intellij.psi.PsiResourceVariable;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiSuperExpression;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchExpression;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.PsiYieldStatement;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class UninitializedReadCollector {

  private final Set<PsiExpression> uninitializedReads;
  private int counter = 0;

  public UninitializedReadCollector() {
    uninitializedReads = new HashSet<>();
  }

  public PsiExpression @NotNull [] getUninitializedReads() {
    return uninitializedReads.toArray(PsiExpression.EMPTY_ARRAY);
  }

  public boolean blockAssignsVariable(@Nullable PsiCodeBlock block, @NotNull PsiVariable variable) {
    return blockAssignsVariable(block, variable, counter, new HashSet<>());
  }

  private boolean blockAssignsVariable(@Nullable PsiCodeBlock block, @NotNull PsiVariable variable,
                                       int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    if (counter != stamp) {
      return true;
    }
    if (block == null) {
      return false;
    }
    for (PsiStatement statement : block.getStatements()) {
      if (statementAssignsVariable(statement, variable, stamp, checkedMethods)) {
        return true;
      }
      if (counter != stamp) {
        return true;
      }
    }
    return false;
  }

  private boolean statementAssignsVariable(@Nullable PsiStatement statement, @NotNull PsiVariable variable,
                                           int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    if (statement == null) {
      return false;
    }
    if (ExceptionUtils.statementThrowsException(statement)) {
      return true;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiAssertStatement ||
        statement instanceof PsiEmptyStatement) {
      return false;
    }
    else if (statement instanceof PsiReturnStatement returnStatement) {
      return expressionAssignsVariable(returnStatement.getReturnValue(), variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiThrowStatement throwStatement) {
      return expressionAssignsVariable(throwStatement.getException(), variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiExpressionListStatement list) {
      for (PsiExpression expression : list.getExpressionList().getExpressions()) {
        if (expressionAssignsVariable(expression, variable, stamp, checkedMethods)) {
          return true;
        }
      }
      return false;
    }
    else if (statement instanceof PsiExpressionStatement expressionStatement) {
      return expressionAssignsVariable(expressionStatement.getExpression(), variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiDeclarationStatement declarationStatement) {
      return declarationStatementAssignsVariable(declarationStatement, variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiForStatement forStatement) {
      return forStatementAssignsVariable(forStatement, variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiForeachStatement) {
      return false;
    }
    else if (statement instanceof PsiForeachPatternStatement) { //not throw errors
      return false;
    }
    else if (statement instanceof PsiWhileStatement whileStatement) {
      return whileStatementAssignsVariable(whileStatement, variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiDoWhileStatement doWhileStatement) {
      return doWhileAssignsVariable(doWhileStatement, variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiSynchronizedStatement synchronizedStatement) {
      return blockAssignsVariable(synchronizedStatement.getBody(), variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiBlockStatement blockStatement) {
      return blockAssignsVariable(blockStatement.getCodeBlock(), variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiLabeledStatement labeledStatement) {
      return statementAssignsVariable(labeledStatement.getStatement(), variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiIfStatement ifStatement) {
      return ifStatementAssignsVariable(ifStatement, variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiTryStatement tryStatement) {
      return tryStatementAssignsVariable(tryStatement, variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiSwitchStatement switchStatement) {
      return switchBlockAssignsVariable(switchStatement, variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    else if (statement instanceof PsiSwitchLabeledRuleStatement switchLabeledRuleStatement) {
      return statementAssignsVariable(switchLabeledRuleStatement.getBody(), variable, stamp, checkedMethods);
    }
    else if (statement instanceof PsiYieldStatement yieldStatement) {
      return expressionAssignsVariable(yieldStatement.getExpression(), variable, stamp, checkedMethods);
    }
    else {
      assert false : "unknown statement: " + statement;
      return false;
    }
  }

  private boolean switchBlockAssignsVariable(@NotNull PsiSwitchBlock switchBlock, @NotNull PsiVariable variable,
                                             int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    if (expressionAssignsVariable(switchBlock.getExpression(), variable, stamp, checkedMethods)) {
      return true;
    }
    final PsiCodeBlock body = switchBlock.getBody();
    if (body == null) {
      return false;
    }
    final PsiStatement[] statements = body.getStatements();
    boolean containsDefault = false;
    boolean assigns = false;
    for (int i = 0; i < statements.length; i++) {
      final PsiStatement statement = statements[i];
      if (statement instanceof PsiSwitchLabelStatement labelStatement) {
        if (i == statements.length - 1) {
          return false;
        }
        if (labelStatement.isDefaultCase()) {
          containsDefault = true;
        }
        assigns = false;
      }
      else if (statement instanceof PsiBreakStatement breakStatement) {
        if (breakStatement.getLabelIdentifier() != null || !assigns) {
          return false;
        }
      }
      else if (statement instanceof PsiYieldStatement yieldStatement) {
        PsiExpression valueExpression = yieldStatement.getExpression();
        if (!assigns) {
          if (expressionAssignsVariable(valueExpression, variable, stamp, checkedMethods)) {
            assigns = true;
          }
          else {
            return false;
          }
        }
      }
      else {
        assigns |= statementAssignsVariable(statement, variable, stamp, checkedMethods);
        if (i == statements.length - 1 && !assigns) {
          return false;
        }
      }
    }
    return assigns && (containsDefault || switchBlock instanceof PsiSwitchExpression);
  }

  private boolean declarationStatementAssignsVariable(@NotNull PsiDeclarationStatement declarationStatement, @NotNull PsiVariable variable,
                                                      int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    for (PsiElement element : declarationStatement.getDeclaredElements()) {
      if (element instanceof PsiVariable variableElement) {
        if (expressionAssignsVariable(variableElement.getInitializer(), variable, stamp, checkedMethods)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean tryStatementAssignsVariable(@NotNull PsiTryStatement tryStatement, @NotNull PsiVariable variable,
                                              int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      for (PsiResourceListElement resource : resourceList) {
        if (resource instanceof PsiResourceVariable resourceVariable) {
          if (expressionAssignsVariable(resourceVariable.getInitializer(), variable, stamp, checkedMethods)) {
            return true;
          }
        }
      }
    }
    boolean initializedInTryOrCatch = blockAssignsVariable(tryStatement.getTryBlock(), variable, stamp, checkedMethods);
    for (PsiCodeBlock catchBlock : tryStatement.getCatchBlocks()) {
      initializedInTryOrCatch &= blockAssignsVariable(catchBlock, variable, stamp, checkedMethods);
    }
    if (initializedInTryOrCatch) {
      return true;
    }
    return blockAssignsVariable(tryStatement.getFinallyBlock(), variable, stamp, checkedMethods);
  }

  private boolean ifStatementAssignsVariable(@NotNull PsiIfStatement ifStatement, @NotNull PsiVariable variable,
                                             int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    if (expressionAssignsVariable(ifStatement.getCondition(), variable, stamp, checkedMethods)) {
      return true;
    }
    return statementAssignsVariable(ifStatement.getThenBranch(), variable, stamp, checkedMethods) &&
           statementAssignsVariable(ifStatement.getElseBranch(), variable, stamp, checkedMethods);
  }

  private boolean doWhileAssignsVariable(@NotNull PsiDoWhileStatement doWhileStatement, @NotNull PsiVariable variable,
                                         int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    return statementAssignsVariable(doWhileStatement.getBody(), variable, stamp, checkedMethods) ||
           expressionAssignsVariable(doWhileStatement.getCondition(), variable, stamp, checkedMethods);
  }

  private boolean whileStatementAssignsVariable(@NotNull PsiWhileStatement whileStatement, @NotNull PsiVariable variable,
                                                int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    final PsiExpression condition = whileStatement.getCondition();
    if (expressionAssignsVariable(condition, variable, stamp, checkedMethods)) {
      return true;
    }
    return BoolUtils.isTrue(condition) && statementAssignsVariable(whileStatement.getBody(), variable, stamp, checkedMethods);
  }

  private boolean forStatementAssignsVariable(@NotNull PsiForStatement forStatement, @NotNull PsiVariable variable,
                                              int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    if (statementAssignsVariable(forStatement.getInitialization(), variable, stamp, checkedMethods)) {
      return true;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (expressionAssignsVariable(condition, variable, stamp, checkedMethods)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      if (statementAssignsVariable(forStatement.getBody(), variable, stamp, checkedMethods)) {
        return true;
      }
      if (statementAssignsVariable(forStatement.getUpdate(), variable, stamp, checkedMethods)) {
        return true;
      }
    }
    return false;
  }

  private boolean expressionAssignsVariable(@Nullable PsiExpression expression, @NotNull PsiVariable variable,
                                            int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    if (counter != stamp) {
      return true;
    }
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiThisExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiSuperExpression ||
        expression instanceof PsiClassObjectAccessExpression ||
        expression instanceof PsiLambdaExpression) {
      return false;
    }
    else if (expression instanceof PsiReferenceExpression referenceExpression) {
      return referenceExpressionAssignsVariable(referenceExpression, variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiMethodCallExpression callExpression) {
      return methodCallAssignsVariable(callExpression, variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiNewExpression newExpression) {
      return newExpressionAssignsVariable(newExpression, variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiArrayInitializerExpression array) {
      for (PsiExpression initializer : array.getInitializers()) {
        if (expressionAssignsVariable(initializer, variable, stamp, checkedMethods)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiTypeCastExpression typeCast) {
      return expressionAssignsVariable(typeCast.getOperand(), variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiArrayAccessExpression accessExpression) {
      return expressionAssignsVariable(accessExpression.getArrayExpression(), variable, stamp, checkedMethods) ||
             expressionAssignsVariable(accessExpression.getIndexExpression(), variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiUnaryExpression unaryExpression) {
      return expressionAssignsVariable(unaryExpression.getOperand(), variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (expressionAssignsVariable(operand, variable, stamp, checkedMethods)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiConditionalExpression conditional) {
      if (expressionAssignsVariable(conditional.getCondition(), variable, stamp, checkedMethods)) {
        return true;
      }
      return expressionAssignsVariable(conditional.getThenExpression(), variable, stamp, checkedMethods)
             && expressionAssignsVariable(conditional.getElseExpression(), variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiAssignmentExpression assignment) {
      return assignmentExpressionAssignsVariable(assignment, variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      return expressionAssignsVariable(parenthesizedExpression.getExpression(), variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiInstanceOfExpression instanceOfExpression) {
      return expressionAssignsVariable(instanceOfExpression.getOperand(), variable, stamp, checkedMethods);
    }
    else if (expression instanceof PsiSwitchExpression switchExpression) {
      return switchBlockAssignsVariable(switchExpression, variable, stamp, checkedMethods);
    }
    else {
      return false;
    }
  }

  private boolean assignmentExpressionAssignsVariable(@NotNull PsiAssignmentExpression assignment, @NotNull PsiVariable variable,
    int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
    if (expressionAssignsVariable(lhs, variable, stamp, checkedMethods)) {
      return true;
    }
    if (expressionAssignsVariable(assignment.getRExpression(), variable, stamp, checkedMethods)) {
      return true;
    }
    if (lhs instanceof PsiReferenceExpression) {
      final PsiElement element = ((PsiReference)lhs).resolve();
      if (element != null && element.equals(variable)) {
        return true;
      }
    }
    return false;
  }

  private boolean referenceExpressionAssignsVariable(@NotNull PsiReferenceExpression referenceExpression, @NotNull PsiVariable variable,
                                                     int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
    if (expressionAssignsVariable(qualifierExpression, variable, stamp, checkedMethods)) {
      return true;
    }
    if (variable.equals(referenceExpression.resolve())) {
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(referenceExpression);
      if (parent instanceof PsiAssignmentExpression assignmentExpression) {
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if (rhs != null && rhs.equals(referenceExpression)) {
          checkReferenceExpression(referenceExpression, variable, qualifierExpression);
        }
      }
      else if (!(parent instanceof PsiExpression expression) || !ComparisonUtils.isNullComparison(expression)) {
        checkReferenceExpression(referenceExpression, variable, qualifierExpression);
      }
    }
    return false;
  }

  private void checkReferenceExpression(PsiReferenceExpression referenceExpression, PsiVariable variable,
                                        PsiExpression qualifierExpression) {
    if (!referenceExpression.isQualified() || qualifierExpression instanceof PsiThisExpression) {
      uninitializedReads.add(referenceExpression);
      counter++;
    }
    else if (variable.hasModifierProperty(PsiModifier.STATIC) && qualifierExpression instanceof PsiReferenceExpression reference) {
      final PsiElement target = reference.resolve();
      if (target instanceof PsiClass) {
        if (target.equals(PsiTreeUtil.getParentOfType(variable, PsiClass.class))) {
          uninitializedReads.add(referenceExpression);
          counter++;
        }
      }
    }
  }

  private boolean newExpressionAssignsVariable(@NotNull PsiNewExpression newExpression, @NotNull PsiVariable variable,
                                               int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      for (PsiExpression arg : argumentList.getExpressions()) {
        if (expressionAssignsVariable(arg, variable, stamp, checkedMethods)) {
          return true;
        }
      }
    }
    if (expressionAssignsVariable(newExpression.getArrayInitializer(), variable, stamp, checkedMethods)) {
      return true;
    }
    for (PsiExpression dim : newExpression.getArrayDimensions()) {
      if (expressionAssignsVariable(dim, variable, stamp, checkedMethods)) {
        return true;
      }
    }
    return false;
  }

  private boolean methodCallAssignsVariable(@NotNull PsiMethodCallExpression callExpression, @NotNull PsiVariable variable,
                                            int stamp, @NotNull Set<MethodSignature> checkedMethods) {
    if (expressionAssignsVariable(callExpression.getMethodExpression(), variable, stamp, checkedMethods)) {
      return true;
    }
    for (PsiExpression argument : callExpression.getArgumentList().getExpressions()) {
      if (expressionAssignsVariable(argument, variable, stamp, checkedMethods)) {
        return true;
      }
    }
    final PsiMethod method = callExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    if (!checkedMethods.add(method.getSignature(PsiSubstitutor.EMPTY))) {
      return false;
    }
    final PsiClass containingClass = PsiUtil.getContainingClass(callExpression);
    final PsiClass calledClass = method.getContainingClass();

    // Can remark out this block to continue chase outside of of
    // current class
    if (calledClass == null || !calledClass.equals(containingClass)) {
      return false;
    }

    if (method.hasModifierProperty(PsiModifier.STATIC)
        || method.isConstructor()
        || method.hasModifierProperty(PsiModifier.PRIVATE)
        || method.hasModifierProperty(PsiModifier.FINAL)
        || calledClass.hasModifierProperty(PsiModifier.FINAL)) {
      return blockAssignsVariable(method.getBody(), variable, stamp, checkedMethods);
    }
    return false;
  }
}