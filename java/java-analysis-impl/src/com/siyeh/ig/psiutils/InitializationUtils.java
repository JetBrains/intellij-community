/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class InitializationUtils {

  private InitializationUtils() {}

  public static boolean methodAssignsVariableOrFails(@Nullable PsiMethod method, @NotNull PsiVariable variable) {
    return methodAssignsVariableOrFails(method, variable, false);
  }

  public static boolean expressionAssignsVariableOrFails(@Nullable PsiExpression expression, @NotNull PsiVariable variable) {
    return expressionAssignsVariableOrFails(expression, variable, new HashSet<>(), true);
  }

  public static boolean methodAssignsVariableOrFails(@Nullable PsiMethod method, @NotNull PsiVariable variable, boolean strict) {
    if (method == null) {
      return false;
    }
    return blockAssignsVariableOrFails(method.getBody(), variable, strict);
  }

  public static boolean blockAssignsVariableOrFails(@Nullable PsiCodeBlock block, @NotNull PsiVariable variable) {
    return blockAssignsVariableOrFails(block, variable, false);
  }

  public static boolean blockAssignsVariableOrFails(@Nullable PsiCodeBlock block, @NotNull PsiVariable variable, boolean strict) {
    return blockAssignsVariableOrFails(block, variable, new HashSet<>(), strict);
  }

  private static boolean blockAssignsVariableOrFails(@Nullable PsiCodeBlock block, @NotNull PsiVariable variable,
                                                     @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    if (block == null) {
      return false;
    }
    int assignmentCount = 0;
    for (final PsiStatement statement : block.getStatements()) {
      if (statementAssignsVariableOrFails(statement, variable, checkedMethods, strict)) {
        if (strict) {
          assignmentCount++;
        }
        else {
          return true;
        }
      }
    }
    return assignmentCount == 1;
  }

  private static boolean statementAssignsVariableOrFails(@Nullable PsiStatement statement, PsiVariable variable,
                                                         @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    if (statement == null) {
      return false;
    }
    if (ExceptionUtils.statementThrowsException(statement)) {
      return true;
    }
    if (statement instanceof PsiBreakStatement ||
        statement instanceof PsiContinueStatement ||
        statement instanceof PsiAssertStatement ||
        statement instanceof PsiEmptyStatement ||
        statement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    else if (statement instanceof PsiReturnStatement returnStatement) {
      return expressionAssignsVariableOrFails(returnStatement.getReturnValue(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiThrowStatement throwStatement) {
      return expressionAssignsVariableOrFails(throwStatement.getException(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiExpressionListStatement list) {
      final PsiExpressionList expressionList = list.getExpressionList();
      for (final PsiExpression expression : expressionList.getExpressions()) {
        if (expressionAssignsVariableOrFails(expression, variable, checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (statement instanceof PsiExpressionStatement expressionStatement) {
      return expressionAssignsVariableOrFails(expressionStatement.getExpression(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiDeclarationStatement declarationStatement) {
      return declarationStatementAssignsVariableOrFails(declarationStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiForStatement forStatement) {
      return forStatementAssignsVariableOrFails(forStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiForeachStatement foreachStatement) {
      return foreachStatementAssignsVariableOrFails(foreachStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiWhileStatement whileStatement) {
      return whileStatementAssignsVariableOrFails(whileStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiDoWhileStatement doWhileStatement) {
      return doWhileAssignsVariableOrFails(doWhileStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiSynchronizedStatement synchronizedStatement) {
      return blockAssignsVariableOrFails(synchronizedStatement.getBody(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiBlockStatement blockStatement) {
      return blockAssignsVariableOrFails(blockStatement.getCodeBlock(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiLabeledStatement labeledStatement) {
      return statementAssignsVariableOrFails(labeledStatement.getStatement(), variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiIfStatement ifStatement) {
      return ifStatementAssignsVariableOrFails(ifStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiTryStatement tryStatement) {
      return tryStatementAssignsVariableOrFails(tryStatement, variable, checkedMethods, strict);
    }
    else if (statement instanceof PsiSwitchStatement switchStatement) {
      return switchStatementAssignsVariableOrFails(switchStatement, variable, checkedMethods, strict);
    }
    else {
      // unknown statement type
      return false;
    }
  }

  private static boolean switchStatementAssignsVariableOrFails(@NotNull PsiSwitchStatement switchStatement, @NotNull PsiVariable variable,
                                                               @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpression expression = switchStatement.getExpression();
    if (expressionAssignsVariableOrFails(expression, variable, checkedMethods, strict)) {
      return true;
    }
    final PsiCodeBlock body = switchStatement.getBody();
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
        if (breakStatement.getLabelIdentifier() != null) {
          return false;
        }
        if (!assigns) {
          return false;
        }
        assigns = false;
      }
      else {
        assigns |= statementAssignsVariableOrFails(statement, variable, checkedMethods, strict);
        if (i == statements.length - 1 && !assigns) {
          return false;
        }
      }
    }
    return containsDefault;
  }

  private static boolean declarationStatementAssignsVariableOrFails(PsiDeclarationStatement declarationStatement, PsiVariable variable,
                                                                    Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiElement[] elements = declarationStatement.getDeclaredElements();
    for (PsiElement element : elements) {
      if (element instanceof PsiVariable declaredVariable) {
        if (expressionAssignsVariableOrFails(declaredVariable.getInitializer(), variable, checkedMethods, strict)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean tryStatementAssignsVariableOrFails(@NotNull PsiTryStatement tryStatement, PsiVariable variable,
                                                            @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      for (PsiResourceListElement resource : resourceList) {
        if (resource instanceof PsiResourceVariable) {
          final PsiExpression initializer = ((PsiResourceVariable)resource).getInitializer();
          if (expressionAssignsVariableOrFails(initializer, variable, checkedMethods, strict)) {
            return true;
          }
        }
      }
    }
    boolean initializedInTryAndCatch = blockAssignsVariableOrFails(tryStatement.getTryBlock(), variable, checkedMethods, strict);
    final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (final PsiCodeBlock catchBlock : catchBlocks) {
      if (strict) {
        initializedInTryAndCatch &= ExceptionUtils.blockThrowsException(catchBlock);
      }
      else {
        initializedInTryAndCatch &= blockAssignsVariableOrFails(catchBlock, variable, checkedMethods, false);
      }
    }
    return initializedInTryAndCatch || blockAssignsVariableOrFails(tryStatement.getFinallyBlock(), variable, checkedMethods, strict);
  }

  private static boolean ifStatementAssignsVariableOrFails(@NotNull PsiIfStatement ifStatement, PsiVariable variable,
                                                           @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpression condition = ifStatement.getCondition();
    if (expressionAssignsVariableOrFails(condition, variable, checkedMethods, strict)) {
      return true;
    }
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (BoolUtils.isTrue(condition)) {
      return statementAssignsVariableOrFails(thenBranch, variable, checkedMethods, strict);
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (BoolUtils.isFalse(condition)) {
      return statementAssignsVariableOrFails(elseBranch, variable, checkedMethods, strict);
    }
    return statementAssignsVariableOrFails(thenBranch, variable, checkedMethods, strict) &&
           statementAssignsVariableOrFails(elseBranch, variable, checkedMethods, strict);
  }

  private static boolean doWhileAssignsVariableOrFails(@NotNull PsiDoWhileStatement doWhileStatement, PsiVariable variable,
                                                       @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    return statementAssignsVariableOrFails(doWhileStatement.getBody(), variable, checkedMethods, strict) ||
           expressionAssignsVariableOrFails(doWhileStatement.getCondition(), variable, checkedMethods, strict);
  }

  private static boolean whileStatementAssignsVariableOrFails(@NotNull PsiWhileStatement whileStatement, PsiVariable variable,
                                                              @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpression condition = whileStatement.getCondition();
    if (expressionAssignsVariableOrFails(condition, variable, checkedMethods, strict)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      final PsiStatement body = whileStatement.getBody();
      return statementAssignsVariableOrFails(body, variable, checkedMethods, strict);
    }
    return false;
  }

  private static boolean forStatementAssignsVariableOrFails(@NotNull PsiForStatement forStatement, PsiVariable variable,
                                                            @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    if (statementAssignsVariableOrFails(forStatement.getInitialization(), variable, checkedMethods, strict)) {
      return true;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (expressionAssignsVariableOrFails(condition, variable, checkedMethods, strict)) {
      return true;
    }
    if (BoolUtils.isTrue(condition)) {
      if (statementAssignsVariableOrFails(forStatement.getBody(), variable, checkedMethods, strict)) {
        return true;
      }
      return statementAssignsVariableOrFails(forStatement.getUpdate(), variable, checkedMethods, strict);
    }
    return false;
  }

  private static boolean foreachStatementAssignsVariableOrFails(@NotNull PsiForeachStatement foreachStatement, PsiVariable field,
                                                                @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    return expressionAssignsVariableOrFails(foreachStatement.getIteratedValue(), field, checkedMethods, strict);
  }

  private static boolean expressionAssignsVariableOrFails(@Nullable PsiExpression expression, PsiVariable variable,
                                                          @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiThisExpression ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiSuperExpression ||
        expression instanceof PsiClassObjectAccessExpression ||
        expression instanceof PsiReferenceExpression) {
      return false;
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      return expressionAssignsVariableOrFails(parenthesizedExpression.getExpression(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiMethodCallExpression methodCallExpression) {
      return methodCallAssignsVariableOrFails(methodCallExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiNewExpression newExpression) {
      return newExpressionAssignsVariableOrFails(newExpression, variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiArrayInitializerExpression array) {
      for (final PsiExpression initializer : array.getInitializers()) {
        if (expressionAssignsVariableOrFails(initializer, variable, checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiTypeCastExpression typeCast) {
      return expressionAssignsVariableOrFails(typeCast.getOperand(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiArrayAccessExpression accessExpression) {
      return expressionAssignsVariableOrFails(accessExpression.getArrayExpression(), variable, checkedMethods, strict) ||
             expressionAssignsVariableOrFails(accessExpression.getIndexExpression(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiUnaryExpression unaryOperation) {
      return expressionAssignsVariableOrFails(unaryOperation.getOperand(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      for (PsiExpression operand : polyadicExpression.getOperands()) {
        if (expressionAssignsVariableOrFails(operand, variable, checkedMethods, strict)) {
          return true;
        }
      }
      return false;
    }
    else if (expression instanceof PsiConditionalExpression conditional) {
      if (expressionAssignsVariableOrFails(conditional.getCondition(), variable, checkedMethods, strict)) {
        return true;
      }
      return expressionAssignsVariableOrFails(conditional.getThenExpression(), variable, checkedMethods, strict) &&
             expressionAssignsVariableOrFails(conditional.getElseExpression(), variable, checkedMethods, strict);
    }
    else if (expression instanceof PsiAssignmentExpression assignment) {
      final PsiExpression lhs = assignment.getLExpression();
      if (expressionAssignsVariableOrFails(lhs, variable, checkedMethods, strict)) {
        return true;
      }
      if (expressionAssignsVariableOrFails(assignment.getRExpression(), variable, checkedMethods, strict)) {
        return true;
      }
      if (lhs instanceof PsiReferenceExpression) {
        final PsiElement element = ((PsiReference)lhs).resolve();
        return variable.equals(element);
      }
      return false;
    }
    else if (expression instanceof PsiInstanceOfExpression instanceOfExpression) {
      return expressionAssignsVariableOrFails(instanceOfExpression.getOperand(), variable, checkedMethods, strict);
    }
    else {
      return false;
    }
  }

  private static boolean newExpressionAssignsVariableOrFails(@NotNull PsiNewExpression newExpression, PsiVariable variable,
                                                             @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList != null) {
      for (final PsiExpression argument : argumentList.getExpressions()) {
        if (expressionAssignsVariableOrFails(argument, variable, checkedMethods, strict)) {
          return true;
        }
      }
    }
    if (expressionAssignsVariableOrFails(newExpression.getArrayInitializer(), variable, checkedMethods, strict)) {
      return true;
    }
    for (final PsiExpression dimension : newExpression.getArrayDimensions()) {
      if (expressionAssignsVariableOrFails(dimension, variable, checkedMethods, strict)) {
        return true;
      }
    }
    return false;
  }

  private static boolean methodCallAssignsVariableOrFails(@NotNull PsiMethodCallExpression callExpression, PsiVariable variable,
                                                          @NotNull Set<MethodSignature> checkedMethods, boolean strict) {
    final PsiExpressionList argumentList = callExpression.getArgumentList();
    for (final PsiExpression argument : argumentList.getExpressions()) {
      if (expressionAssignsVariableOrFails(argument, variable, checkedMethods, strict)) {
        return true;
      }
    }
    if (expressionAssignsVariableOrFails(callExpression.getMethodExpression(), variable, checkedMethods, strict)) {
      return true;
    }
    final PsiMethod method = callExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
    if (!checkedMethods.add(methodSignature)) {
      return false;
    }
    final PsiClass containingClass = PsiUtil.getContainingClass(callExpression);
    final PsiClass calledClass = method.getContainingClass();
    if (calledClass == null || !calledClass.equals(containingClass)) {
      return false;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)
        || method.hasModifierProperty(PsiModifier.PRIVATE)
        || method.hasModifierProperty(PsiModifier.FINAL)
        || method.isConstructor()
        || calledClass.hasModifierProperty(PsiModifier.FINAL)) {
      return blockAssignsVariableOrFails(method.getBody(), variable, checkedMethods, strict);
    }
    return false;
  }

  public static boolean isInitializedInConstructors(PsiField field, PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return false;
    }
    for (final PsiMethod constructor : constructors) {
      if (!methodAssignsVariableOrFails(constructor, field)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isInitializedInInitializer(@NotNull PsiField field, @NotNull PsiClass aClass) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    for (final PsiClassInitializer initializer : initializers) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        continue;
      }
      final PsiCodeBlock body = initializer.getBody();
      if (blockAssignsVariableOrFails(body, field)) {
        return true;
      }
    }
    return false;
  }
}