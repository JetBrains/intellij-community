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

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiBreakStatement;
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
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSwitchBlock;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.PsiWhileStatement;
import com.intellij.psi.PsiYieldStatement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

public final class RecursionUtils {

  private RecursionUtils() {}

  public static boolean statementMayReturnBeforeRecursing(@Nullable PsiStatement statement, PsiMethod method) {
    return switch (statement) {
      case PsiAssertStatement ignore -> false;
      case PsiBlockStatement s -> codeBlockMayReturnBeforeRecursing(s.getCodeBlock(), method, false);
      case PsiBreakStatement ignore -> false;
      case PsiContinueStatement ignore -> false;
      case PsiDeclarationStatement ignore -> false;
      case PsiDoWhileStatement s -> statementMayReturnBeforeRecursing(s.getBody(), method);
      case PsiEmptyStatement ignore -> false;
      case PsiExpressionListStatement ignore -> false;
      case PsiExpressionStatement ignore -> false;
      case PsiForeachStatement s -> foreachStatementMayReturnBeforeRecursing(s, method);
      case PsiForStatement s -> forStatementMayReturnBeforeRecursing(s, method);
      case PsiIfStatement s -> ifStatementMayReturnBeforeRecursing(s, method);
      case PsiLabeledStatement s -> statementMayReturnBeforeRecursing(s.getStatement(), method);
      case PsiReturnStatement s -> returnStatementMayReturnBeforeRecursing(method, s);
      case PsiSwitchStatement s -> switchBlockMayReturnBeforeRecursing(s, method);
      case PsiSynchronizedStatement s -> codeBlockMayReturnBeforeRecursing(s.getBody(), method, false);
      case PsiThrowStatement ignore -> false;
      case PsiTryStatement s -> tryStatementMayReturnBeforeRecursing(s, method);
      case PsiWhileStatement s -> whileStatementMayReturnBeforeRecursing(s, method);
      case null, default -> true;
    };
  }

  private static boolean returnStatementMayReturnBeforeRecursing(PsiMethod method, PsiReturnStatement s) {
    final PsiExpression returnValue = s.getReturnValue();
    return returnValue == null || !expressionDefinitelyRecurses(returnValue, method);
  }

  private static boolean whileStatementMayReturnBeforeRecursing(PsiWhileStatement loopStatement, PsiMethod method) {
    return !expressionDefinitelyRecurses(loopStatement.getCondition(), method) 
           && statementMayReturnBeforeRecursing(loopStatement.getBody(), method);
  }

  private static boolean forStatementMayReturnBeforeRecursing(PsiForStatement loopStatement, PsiMethod method) {
    if (statementMayReturnBeforeRecursing(loopStatement.getInitialization(), method)) {
      return true;
    }
    return !expressionDefinitelyRecurses(loopStatement.getCondition(), method) 
           && statementMayReturnBeforeRecursing(loopStatement.getBody(), method);
  }

  private static boolean foreachStatementMayReturnBeforeRecursing(PsiForeachStatement loopStatement, PsiMethod method) {
    return !expressionDefinitelyRecurses(loopStatement.getIteratedValue(), method) 
           && statementMayReturnBeforeRecursing(loopStatement.getBody(), method);
  }

  private static boolean switchBlockMayReturnBeforeRecursing(PsiSwitchBlock switchStatement, PsiMethod method) {
    final PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return true;
    }
    for (PsiStatement statement : body.getStatements()) {
      if (statement instanceof PsiSwitchLabelStatement) {
        continue;
      }
      if (statement instanceof PsiSwitchLabeledRuleStatement labeledRuleStatement) {
        if (statementMayReturnBeforeRecursing(labeledRuleStatement.getBody(), method)) {
          return true;
        }
        continue;
      }
      if (statementMayReturnBeforeRecursing(statement, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean tryStatementMayReturnBeforeRecursing(PsiTryStatement tryStatement, PsiMethod method) {
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      if (codeBlockMayReturnBeforeRecursing(finallyBlock, method, false)) {
        return true;
      }
      if (codeBlockDefinitelyRecurses(finallyBlock, method)) {
        return false;
      }
    }
    if (codeBlockMayReturnBeforeRecursing(tryStatement.getTryBlock(), method, false)) {
      return true;
    }
    for (PsiCodeBlock catchBlock : tryStatement.getCatchBlocks()) {
      if (codeBlockMayReturnBeforeRecursing(catchBlock, method, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementMayReturnBeforeRecursing(PsiIfStatement ifStatement, PsiMethod method) {
    if (expressionDefinitelyRecurses(ifStatement.getCondition(), method)) {
      return false;
    }
    if (statementMayReturnBeforeRecursing(ifStatement.getThenBranch(), method)) {
      return true;
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    return elseBranch != null && statementMayReturnBeforeRecursing(elseBranch, method);
  }

  private static boolean codeBlockMayReturnBeforeRecursing(PsiCodeBlock block, PsiMethod method, boolean endsInImplicitReturn) {
    if (block == null) {
      return true;
    }
    for (PsiStatement statement : block.getStatements()) {
      if (statementMayReturnBeforeRecursing(statement, method)) {
        return true;
      }
      if (statementDefinitelyRecurses(statement, method)) {
        return false;
      }
    }
    return endsInImplicitReturn;
  }

  public static boolean methodMayRecurse(@NotNull PsiMethod method) {
    final RecursionVisitor recursionVisitor = new RecursionVisitor(method);
    method.accept(recursionVisitor);
    return recursionVisitor.isRecursive();
  }

  private static boolean expressionDefinitelyRecurses(@Nullable PsiExpression exp, PsiMethod method) {
    return switch (exp) {
      case PsiArrayAccessExpression e -> arrayAccessExpressionDefinitelyRecurses(e, method);
      case PsiArrayInitializerExpression e -> arrayInitializerExpressionDefinitelyRecurses(e, method);
      case PsiAssignmentExpression e -> assignmentExpressionDefinitelyRecurses(e, method);
      case PsiBinaryExpression e -> binaryExpressionDefinitelyRecurses(e, method);
      case PsiConditionalExpression e -> conditionalExpressionDefinitelyRecurses(e, method);
      case PsiInstanceOfExpression e -> expressionDefinitelyRecurses(e.getOperand(), method);
      case PsiMethodCallExpression e -> methodCallExpressionDefinitelyRecurses(e, method);
      case PsiNewExpression e -> newExpressionDefinitelyRecurses(e, method);
      case PsiParenthesizedExpression e -> expressionDefinitelyRecurses(e.getExpression(), method);
      case PsiReferenceExpression e -> expressionDefinitelyRecurses(e.getQualifierExpression(), method);
      case PsiTypeCastExpression e -> expressionDefinitelyRecurses(e.getOperand(), method);
      case PsiUnaryExpression e -> expressionDefinitelyRecurses(e.getOperand(), method);
      case null, default -> false;
    };
  }

  private static boolean conditionalExpressionDefinitelyRecurses(PsiConditionalExpression expression, PsiMethod method) {
    if (expressionDefinitelyRecurses(expression.getCondition(), method)) {
      return true;
    }
    return expressionDefinitelyRecurses(expression.getThenExpression(), method)
           && expressionDefinitelyRecurses(expression.getElseExpression(), method);
  }

  private static boolean binaryExpressionDefinitelyRecurses(PsiBinaryExpression expression, PsiMethod method) {
    if (expressionDefinitelyRecurses(expression.getLOperand(), method)) {
      return true;
    }
    final IElementType tokenType = expression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
      return false;
    }
    return expressionDefinitelyRecurses(expression.getROperand(), method);
  }

  private static boolean arrayAccessExpressionDefinitelyRecurses(PsiArrayAccessExpression expression, PsiMethod method) {
    return expressionDefinitelyRecurses(expression.getArrayExpression(), method)
           || expressionDefinitelyRecurses(expression.getIndexExpression(), method);
  }

  private static boolean arrayInitializerExpressionDefinitelyRecurses(PsiArrayInitializerExpression expression, PsiMethod method) {
    for (PsiExpression initializer : expression.getInitializers()) {
      if (expressionDefinitelyRecurses(initializer, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean assignmentExpressionDefinitelyRecurses(PsiAssignmentExpression assignmentExpression, PsiMethod method) {
    return expressionDefinitelyRecurses(assignmentExpression.getRExpression(), method) 
           || expressionDefinitelyRecurses(assignmentExpression.getLExpression(), method);
  }

  private static boolean newExpressionDefinitelyRecurses(PsiNewExpression exp, PsiMethod method) {
    for (PsiExpression arrayDimension : exp.getArrayDimensions()) {
      if (expressionDefinitelyRecurses(arrayDimension, method)) {
        return true;
      }
    }
    if (expressionDefinitelyRecurses(exp.getArrayInitializer(), method) || expressionDefinitelyRecurses(exp.getQualifier(), method)) {
      return true;
    }
    final PsiExpressionList argumentList = exp.getArgumentList();
    if (argumentList != null) {
      for (PsiExpression arg : argumentList.getExpressions()) {
        if (expressionDefinitelyRecurses(arg, method)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean methodCallExpressionDefinitelyRecurses(PsiMethodCallExpression exp, PsiMethod method) {
    final PsiMethod referencedMethod = exp.resolveMethod();
    if (referencedMethod == null) {
      return false;
    }
    if (methodCallExpressionIndirectDefinitelyRecurses(exp, method)) {
      return true;
    }
    final PsiReferenceExpression methodExpression = exp.getMethodExpression();
    if (referencedMethod.equals(method)) {
      if (method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return true;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        return true;
      }
    }
    if (expressionDefinitelyRecurses(methodExpression.getQualifierExpression(), method)) {
      return true;
    }
    for (PsiExpression arg : exp.getArgumentList().getExpressions()) {
      if (expressionDefinitelyRecurses(arg, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean statementDefinitelyRecurses(PsiStatement statement, PsiMethod method) {
    return switch (statement) {
      case PsiAssertStatement ignore -> false;
      case PsiBlockStatement s -> codeBlockDefinitelyRecurses(s.getCodeBlock(), method);
      case PsiBreakStatement ignore -> false;
      case PsiContinueStatement ignore -> false;
      case PsiDeclarationStatement s -> {
        for (PsiElement declaredElement : s.getDeclaredElements()) {
          if (declaredElement instanceof PsiLocalVariable variable && expressionDefinitelyRecurses(variable.getInitializer(), method)) {
            yield true;
          }
        }
        yield false;
      }
      case PsiDoWhileStatement s -> doWhileStatementDefinitelyRecurses(s, method);
      case PsiEmptyStatement ignore -> false;
      case PsiExpressionListStatement s -> expressionListStatementDefinitelyRecurses(s, method);
      case PsiExpressionStatement s -> expressionDefinitelyRecurses(s.getExpression(), method);
      case PsiForeachStatement s -> expressionDefinitelyRecurses(s.getIteratedValue(), method);
      case PsiForStatement s -> forStatementDefinitelyRecurses(s, method);
      case PsiIfStatement s -> ifStatementDefinitelyRecurses(s, method);
      case PsiLabeledStatement s -> statementDefinitelyRecurses(s.getStatement(), method);
      case PsiReturnStatement s -> {
        final PsiExpression returnValue = s.getReturnValue();
        yield returnValue != null && expressionDefinitelyRecurses(returnValue, method);
      }
      case PsiSwitchStatement s -> expressionDefinitelyRecurses(s.getExpression(), method);
      case PsiSynchronizedStatement s -> codeBlockDefinitelyRecurses(s.getBody(), method);
      case PsiThrowStatement ignore -> false;
      case PsiTryStatement s -> tryStatementDefinitelyRecurses(s, method);
      case PsiWhileStatement s -> whileStatementDefinitelyRecurses(s, method);
      case PsiYieldStatement s -> expressionDefinitelyRecurses(s.getExpression(), method);
      case null, default -> false;
    };
  }

  private static boolean expressionListStatementDefinitelyRecurses(PsiExpressionListStatement statement, PsiMethod method) {
    for (PsiExpression expression : statement.getExpressionList().getExpressions()) {
      if (expressionDefinitelyRecurses(expression, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean tryStatementDefinitelyRecurses(PsiTryStatement tryStatement, PsiMethod method) {
    return codeBlockDefinitelyRecurses(tryStatement.getTryBlock(), method) 
           || codeBlockDefinitelyRecurses(tryStatement.getFinallyBlock(), method);
  }

  private static boolean codeBlockDefinitelyRecurses(PsiCodeBlock block, PsiMethod method) {
    if (block == null) {
      return false;
    }
    for (PsiStatement statement : block.getStatements()) {
      if (statementDefinitelyRecurses(statement, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementDefinitelyRecurses(PsiIfStatement ifStatement, PsiMethod method) {
    final PsiExpression condition = ifStatement.getCondition();
    if (expressionDefinitelyRecurses(condition, method)) {
      return true;
    }
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (thenBranch == null) {
      return false;
    }
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    if (value == Boolean.TRUE) {
      return statementDefinitelyRecurses(thenBranch, method);
    }
    else if (value == Boolean.FALSE) {
      return statementDefinitelyRecurses(elseBranch, method);
    }
    return statementDefinitelyRecurses(thenBranch, method) && statementDefinitelyRecurses(elseBranch, method);
  }

  private static boolean forStatementDefinitelyRecurses(PsiForStatement forStatement, PsiMethod method) {
    if (statementDefinitelyRecurses(forStatement.getInitialization(), method)) {
      return true;
    }
    final PsiExpression condition = forStatement.getCondition();
    if (expressionDefinitelyRecurses(condition, method)) {
      return true;
    }
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    return value == Boolean.TRUE && statementDefinitelyRecurses(forStatement.getBody(), method);
  }

  private static boolean whileStatementDefinitelyRecurses(PsiWhileStatement whileStatement, PsiMethod method) {
    final PsiExpression condition = whileStatement.getCondition();
    if (expressionDefinitelyRecurses(condition, method)) {
      return true;
    }
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    return value == Boolean.TRUE && statementDefinitelyRecurses(whileStatement.getBody(), method);
  }

  private static boolean doWhileStatementDefinitelyRecurses(PsiDoWhileStatement doWhileStatement, PsiMethod method) {
    return statementDefinitelyRecurses(doWhileStatement.getBody(), method) 
           || expressionDefinitelyRecurses(doWhileStatement.getCondition(), method);
  }

  public static boolean methodDefinitelyRecurses(@NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    return body != null && !codeBlockMayReturnBeforeRecursing(body, method, true);
  }

  static boolean methodCallExpressionIndirectDefinitelyRecurses(PsiMethodCallExpression exp, PsiMethod method) {
    BiPredicate<PsiMethodCallExpression, PsiMethod> predicate = MAY_CAUSE_INDIRECT_RECURSION.mapFirst(exp);
    return predicate != null && predicate.test(exp, method);
  }

  private static final CallMapper<BiPredicate<PsiMethodCallExpression, PsiMethod>> MAY_CAUSE_INDIRECT_RECURSION =
    new CallMapper<BiPredicate<PsiMethodCallExpression, PsiMethod>>()
    .register(CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "hashCode", "hash"),
              (callExpression, method) ->  MethodUtils.isHashCode(method) && useThisAsArgument(callExpression))
    .register(CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "toString"),
              (callExpression, method) ->  MethodUtils.isToString(method) && useThisAsArgument(callExpression))
    .register(CallMatcher.staticCall(CommonClassNames.JAVA_LANG_STRING, "valueOf"),
              (callExpression, method) ->  MethodUtils.isToString(method) && useThisAsArgument(callExpression))
    .register(CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OBJECTS, "equals", "deepEquals"),
              (callExpression, method) ->  MethodUtils.isEquals(method) && useThisAsArgument(callExpression));

  private static boolean useThisAsArgument(PsiMethodCallExpression expression) {
    return ContainerUtil.findInstance(expression.getArgumentList().getExpressions(), PsiThisExpression.class) != null;
  }
}
