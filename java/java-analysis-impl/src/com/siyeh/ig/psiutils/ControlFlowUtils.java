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

import com.intellij.codeInsight.BlockUtils;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.siyeh.ig.psiutils.ControlFlowUtils.InitializerUsageStatus.*;

public final class ControlFlowUtils {
  private ControlFlowUtils() { }

  public static boolean isElseIf(PsiIfStatement ifStatement) {
    PsiElement parent = ifStatement.getParent();
    if (parent instanceof PsiCodeBlock block &&
        block.getStatementCount() == 1 &&
        parent.getParent() instanceof PsiBlockStatement bs) {
      parent = bs.getParent();
    }
    if (!(parent instanceof PsiIfStatement parentStatement)) {
      return false;
    }
    final PsiStatement elseBranch = parentStatement.getElseBranch();
    return ifStatement.equals(elseBranch);
  }

  public static boolean statementMayCompleteNormally(@Nullable PsiStatement statement) {
    return statementMayCompleteNormally(statement, null);
  }

  private static boolean statementMayCompleteNormally(@Nullable PsiStatement statement, @Nullable PsiMethod psiMethod) {
    if (statement == null) {
      return true;
    }
    if (statement instanceof PsiBreakStatement || statement instanceof PsiContinueStatement || statement instanceof PsiYieldStatement ||
        statement instanceof PsiReturnStatement || statement instanceof PsiThrowStatement) {
      return false;
    }
    else if (statement instanceof PsiExpressionListStatement || statement instanceof PsiEmptyStatement ||
             statement instanceof PsiAssertStatement || statement instanceof PsiDeclarationStatement ||
             statement instanceof PsiSwitchLabelStatement || statement instanceof PsiForeachStatementBase) {
      return true;
    }
    else if (statement instanceof final PsiExpressionStatement expressionStatement) {
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof final PsiMethodCallExpression methodCallExpression)) {
        return true;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return true;
      }
      if (method.equals(psiMethod)) {
        return false;
      }
      final @NonNls String methodName = method.getName();
      if (!methodName.equals("exit")) {
        return true;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return true;
      }
      final String className = aClass.getQualifiedName();
      return !"java.lang.System".equals(className);
    }
    else if (statement instanceof PsiForStatement forStatement) {
      return forStatementMayCompleteNormally(forStatement);
    }
    else if (statement instanceof PsiWhileStatement whileStatement) {
      return whileStatementMayCompleteNormally(whileStatement);
    }
    else if (statement instanceof PsiDoWhileStatement doWhileStatement) {
      return doWhileStatementMayCompleteNormally(doWhileStatement);
    }
    else if (statement instanceof PsiSynchronizedStatement synchronizedStatement) {
      return codeBlockMayCompleteNormally(synchronizedStatement.getBody(), psiMethod);
    }
    else if (statement instanceof PsiBlockStatement block) {
      return codeBlockMayCompleteNormally(block.getCodeBlock(), psiMethod);
    }
    else if (statement instanceof PsiLabeledStatement labeled) {
      return labeledStatementMayCompleteNormally(labeled, psiMethod);
    }
    else if (statement instanceof PsiIfStatement ifStatement) {
      return ifStatementMayCompleteNormally(ifStatement, psiMethod);
    }
    else if (statement instanceof PsiTryStatement tryStatement) {
      return tryStatementMayCompleteNormally(tryStatement, psiMethod);
    }
    else if (statement instanceof PsiSwitchStatement switchStatement) {
      return switchStatementMayCompleteNormally(switchStatement, psiMethod);
    }
    else if (statement instanceof PsiSwitchLabeledRuleStatement rule) {
      PsiStatement body = rule.getBody();
      return body != null && statementMayCompleteNormally(body, psiMethod);
    }
    else if (statement instanceof PsiTemplateStatement || statement instanceof PsiClassLevelDeclarationStatement) {
      return true;
    }
    else {
      assert false : "unknown statement type: " + statement.getClass();
      return true;
    }
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isEndlessLoop(@Nullable PsiConditionalLoopStatement loopStatement) {
    if (loopStatement == null) return false;
    if (loopStatement instanceof PsiForStatement forStatement) {
      PsiExpression condition = forStatement.getCondition();
      if (condition != null && !BoolUtils.isTrue(condition)) return false;
      return (forStatement.getInitialization() == null || forStatement.getInitialization() instanceof PsiEmptyStatement)
             && (forStatement.getUpdate() == null || forStatement.getUpdate() instanceof PsiEmptyStatement);
    }
    return BoolUtils.isTrue(loopStatement.getCondition());
  }

  private static boolean doWhileStatementMayCompleteNormally(@NotNull PsiDoWhileStatement loopStatement) {
    return conditionalLoopStatementMayCompleteNormally(loopStatement);
  }

  private static boolean whileStatementMayCompleteNormally(@NotNull PsiWhileStatement loopStatement) {
    return conditionalLoopStatementMayCompleteNormally(loopStatement);
  }

  private static boolean forStatementMayCompleteNormally(@NotNull PsiForStatement loopStatement) {
    return conditionalLoopStatementMayCompleteNormally(loopStatement);
  }

  private static boolean conditionalLoopStatementMayCompleteNormally(@NotNull PsiConditionalLoopStatement loopStatement) {
    final PsiExpression condition = loopStatement.getCondition();
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    return value != Boolean.TRUE
           || statementContainsBreakToStatementOrAncestor(loopStatement) || statementContainsContinueToAncestor(loopStatement);
  }

  private static boolean switchStatementMayCompleteNormally(@NotNull PsiSwitchStatement switchStatement, @Nullable PsiMethod method) {
    if (statementIsBreakTarget(switchStatement)) {
      return true;
    }
    final PsiExpression selectorExpression = switchStatement.getExpression();
    if (selectorExpression == null) {
      return true;
    }
    final PsiType selectorType = selectorExpression.getType();
    if (selectorType == null) {
      return true;
    }
    final PsiCodeBlock body = switchStatement.getBody();
    if (body == null) {
      return true;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return true;
    }
    int numCases = 0;
    boolean hasDefaultCase = false, hasUnconditionalPattern = false;
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiSwitchLabelStatementBase switchLabelStatement) {
        if (statement instanceof PsiSwitchLabelStatement) {
          numCases++;
        }
        if (hasDefaultCase || hasUnconditionalPattern) {
          continue;
        }
        if (switchLabelStatement.isDefaultCase()) {
          hasDefaultCase = true;
          continue;
        }
        // this information doesn't exist in spec draft (14.22) for pattern in switch as expected
        // but for now javac considers the switch statement containing at least either
        // case default label element or an unconditional pattern "incomplete normally"
        PsiCaseLabelElementList labelElementList = switchLabelStatement.getCaseLabelElementList();
        if (labelElementList == null) {
          continue;
        }
        for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
          if (labelElement instanceof PsiDefaultCaseLabelElement) {
            hasDefaultCase = true;
          }
          else if (labelElement instanceof PsiPattern) {
            hasUnconditionalPattern = JavaPsiPatternUtil.isUnconditionalForType(labelElement, selectorType);
          }
        }
      }
      else if (statement instanceof final PsiBreakStatement breakStatement && breakStatement.getLabelIdentifier() == null) {
        return true;
      }
    }
    // Actually there is no information about an impact of enum constants on switch statements completing normally in the spec
    // (Unreachable statements)
    // todo comparing to javac that produces some false-negative highlighting in enum switch statements containing all possible constants
    final boolean isEnum = isEnumSwitch(switchStatement);
    if (!hasDefaultCase && !hasUnconditionalPattern && !isEnum) {
      return true;
    }
    if (!hasDefaultCase && !hasUnconditionalPattern) {
      final PsiClass aClass = ((PsiClassType)selectorType).resolve();
      if (aClass == null) {
        return true;
      }
      if (!hasChildrenOfTypeCount(aClass, numCases, PsiEnumConstant.class)) {
        return true;
      }
    }
    // todo replace the code and comments below with the method that helps to understand whether
    // todo we need to check every statement or only the last statement in the code block
    // 14.22. Unreachable Statements
    // We need to check every rule's body not just the last one if the switch block includes the switch rules
    boolean isLabeledRuleSwitch = statements[0] instanceof PsiSwitchLabeledRuleStatement;
    if (isLabeledRuleSwitch) {
      for (PsiStatement statement : statements) {
        if (statementMayCompleteNormally(statement, method)) {
          return true;
        }
      }
      return false;
    }
    return statementMayCompleteNormally(statements[statements.length - 1], method);
  }

  private static boolean isEnumSwitch(PsiSwitchStatement statement) {
    final PsiExpression expression = statement.getExpression();
    if (expression == null) {
      return false;
    }
    final PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClass aClass = ((PsiClassType)type).resolve();
    return aClass != null && aClass.isEnum();
  }

  private static boolean tryStatementMayCompleteNormally(@NotNull PsiTryStatement tryStatement, @Nullable PsiMethod method) {
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      if (!codeBlockMayCompleteNormally(finallyBlock, method)) {
        return false;
      }
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (codeBlockMayCompleteNormally(tryBlock, method)) {
      return true;
    }
    final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (final PsiCodeBlock catchBlock : catchBlocks) {
      if (codeBlockMayCompleteNormally(catchBlock, method)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementMayCompleteNormally(@NotNull PsiIfStatement ifStatement, @Nullable PsiMethod method) {
    final PsiExpression condition = ifStatement.getCondition();
    final Object value = ExpressionUtils.computeConstantExpression(condition);
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (value == Boolean.TRUE) {
      return statementMayCompleteNormally(thenBranch, method);
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (value == Boolean.FALSE) {
      return statementMayCompleteNormally(elseBranch, method);
    }
    // process branch with fewer statements first
    PsiStatement branch1;
    PsiStatement branch2;
    if ((thenBranch == null ? 0 : thenBranch.getTextLength()) < (elseBranch == null ? 0 : elseBranch.getTextLength())) {
      branch1 = thenBranch;
      branch2 = elseBranch;
    }
    else {
      branch2 = thenBranch;
      branch1 = elseBranch;
    }
    return statementMayCompleteNormally(branch1, method) || statementMayCompleteNormally(branch2, method);
  }

  private static boolean labeledStatementMayCompleteNormally(@NotNull PsiLabeledStatement labeledStatement, @Nullable PsiMethod method) {
    final PsiStatement statement = labeledStatement.getStatement();
    if (statement == null) {
      return false;
    }
    return statementMayCompleteNormally(statement, method) || statementContainsBreakToStatementOrAncestor(statement);
  }

  public static boolean codeBlockMayCompleteNormally(@Nullable PsiCodeBlock block) {
    return codeBlockMayCompleteNormally(block, null);
  }

  /**
   * Works like {@link ControlFlowUtils#codeBlockMayCompleteNormally(PsiCodeBlock)} that takes {@code method.getBody()}
   * as an argument, but recursive method calls are considered as breaking execution.
   *
   * @param method to check
   * @return true if method may complete normally
   */
  public static boolean methodMayCompleteNormally(@NotNull PsiMethod method) {
    return codeBlockMayCompleteNormally(method.getBody(), method);
  }

  private static boolean codeBlockMayCompleteNormally(@Nullable PsiCodeBlock block, @Nullable PsiMethod method) {
    if (block == null) {
      return true;
    }
    final PsiStatement[] statements = block.getStatements();
    for (final PsiStatement statement : statements) {
      if (!statementMayCompleteNormally(statement, method)) {
        return false;
      }
    }
    return true;
  }

  private static boolean statementContainsBreakToStatementOrAncestor(@NotNull PsiStatement statement) {
    final BreakFinder breakFinder = new BreakFinder(statement, true);
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  private static boolean statementIsBreakTarget(@NotNull PsiStatement statement) {
    final BreakFinder breakFinder = new BreakFinder(statement, false);
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  private static boolean statementContainsContinueToAncestor(@NotNull PsiStatement statement) {
    PsiElement parent = statement.getParent();
    while (parent instanceof PsiLabeledStatement) {
      statement = (PsiStatement)parent;
      parent = parent.getParent();
    }
    final ContinueToAncestorFinder continueToAncestorFinder = new ContinueToAncestorFinder(statement);
    statement.accept(continueToAncestorFinder);
    return continueToAncestorFinder.continueToAncestorFound();
  }

  public static boolean containsReturn(@NotNull PsiElement element) {
    final ReturnFinder returnFinder = new ReturnFinder();
    element.accept(returnFinder);
    return returnFinder.returnFound();
  }

  public static boolean statementIsContinueTarget(@NotNull PsiStatement statement) {
    final ContinueFinder continueFinder = new ContinueFinder(statement);
    statement.accept(continueFinder);
    return continueFinder.continueFound();
  }

  public static boolean containsSystemExit(@NotNull PsiElement element) {
    final SystemExitFinder systemExitFinder = new SystemExitFinder();
    element.accept(systemExitFinder);
    return systemExitFinder.exitFound();
  }

  public static boolean containsYield(@NotNull PsiElement element){
    final YieldFinder returnFinder = new YieldFinder();
    element.accept(returnFinder);
    return returnFinder.yieldFound();
  }

  public static boolean elementContainsCallToMethod(PsiElement context, @NonNls String containingClassName, PsiType returnType,
    @NonNls String methodName, PsiType... parameterTypes) {
    final MethodCallFinder methodCallFinder = new MethodCallFinder(containingClassName, returnType, methodName, parameterTypes);
    context.accept(methodCallFinder);
    return methodCallFinder.containsCallToMethod();
  }

  public static boolean isInLoop(@NotNull PsiElement element) {
    final PsiLoopStatement loopStatement = PsiTreeUtil.getParentOfType(element, PsiLoopStatement.class, true, PsiClass.class);
    if (loopStatement == null) {
      return false;
    }
    final PsiStatement body = loopStatement.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  public static boolean isInFinallyBlock(@NotNull PsiElement element, @Nullable PsiElement stopAt) {
    PsiElement parent = element.getParent();
    while (parent != null && parent != stopAt && !(parent instanceof PsiMember) && !(parent instanceof PsiLambdaExpression)) {
      if (parent instanceof PsiTryStatement tryStatement) {
        final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null && PsiTreeUtil.isAncestor(finallyBlock, element, true)) {
          return true;
        }
      }
      parent = parent.getParent();
    }
    return false;
  }

  public static boolean isInCatchBlock(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiCatchSection.class, true, PsiClass.class) != null;
  }

  public static boolean isInExitStatement(@NotNull PsiExpression expression) {
    return isInReturnStatementArgument(expression) || isInThrowStatementArgument(expression);
  }

  private static boolean isInReturnStatementArgument(@NotNull PsiExpression expression) {
    return PsiTreeUtil.getParentOfType(expression, PsiReturnStatement.class) != null;
  }

  public static boolean isInThrowStatementArgument(@NotNull PsiExpression expression) {
    return PsiTreeUtil.getParentOfType(expression, PsiThrowStatement.class) != null;
  }

  @Contract("null -> null; !null -> !null")
  public static PsiStatement stripBraces(@Nullable PsiStatement statement) {
    if (statement instanceof PsiBlockStatement block) {
      PsiStatement onlyStatement = getOnlyStatementInBlock(block.getCodeBlock());
      return (onlyStatement != null) ? onlyStatement : block;
    }
    else {
      return statement;
    }
  }


  public static PsiStatement @NotNull [] unwrapBlock(@Nullable PsiStatement statement) {
    final PsiBlockStatement block = ObjectUtils.tryCast(statement, PsiBlockStatement.class);
    if (block != null) {
      return block.getCodeBlock().getStatements();
    }
    return statement == null ? PsiStatement.EMPTY_ARRAY : new PsiStatement[]{statement};
  }

  public static boolean statementCompletesWithStatement(@NotNull PsiElement containingElement, @NotNull PsiStatement statement) {
    PsiElement statementToCheck = statement;
    while (true) {
      if (containingElement.equals(statementToCheck)) {
        return true;
      }
      final PsiElement container = getContainingStatementOrBlock(statementToCheck);
      if (container == null || container instanceof PsiLoopStatement) {
        return false;
      }
      if (container instanceof PsiCodeBlock) {
        if (!statementIsLastInBlock((PsiCodeBlock)container, (PsiStatement)statementToCheck)) {
          return false;
        }
      }

      statementToCheck = container;
      if (statementToCheck instanceof PsiSwitchLabeledRuleStatement) {
        statementToCheck = PsiTreeUtil.getParentOfType(statementToCheck, PsiStatement.class);
      }
    }
  }

  public static boolean blockCompletesWithStatement(@NotNull PsiCodeBlock body, @NotNull PsiStatement statement) {
    PsiElement statementToCheck = statement;
    while (true) {
      final PsiElement container = getContainingStatementOrBlock(statementToCheck);
      if (container == null || container instanceof PsiLoopStatement) {
        return false;
      }
      if (container instanceof PsiCodeBlock) {
        if (!statementIsLastInBlock((PsiCodeBlock)container, (PsiStatement)statementToCheck)) {
          return false;
        }
        if (container.equals(body)) {
          return true;
        }
        statementToCheck = PsiTreeUtil.getParentOfType(container, PsiStatement.class);
      }
      else {
        statementToCheck = container;
        if (statementToCheck instanceof PsiSwitchLabeledRuleStatement) {
          statementToCheck = PsiTreeUtil.getParentOfType(statementToCheck, PsiStatement.class);
        }
      }
    }
  }

  @Contract("null -> null")
  private static @Nullable PsiElement getContainingStatementOrBlock(@Nullable PsiElement statement) {
    return PsiTreeUtil.getParentOfType(statement, PsiStatement.class, PsiCodeBlock.class);
  }

  private static boolean statementIsLastInBlock(@NotNull PsiCodeBlock block, @NotNull PsiStatement statement) {
    for (PsiElement child = block.getLastChild(); child != null; child = child.getPrevSibling()) {
      if (child instanceof PsiStatement childStatement) {
        if (statement.equals(childStatement)) {
          return true;
        }
        if (!(statement instanceof PsiEmptyStatement)) {
          return false;
        }
      }
    }
    return false;
  }

  public static @Nullable PsiStatement getFirstStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
    return PsiTreeUtil.getChildOfType(codeBlock, PsiStatement.class);
  }

  public static @Nullable PsiStatement getLastStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
    if (codeBlock == null) return null;
    for (PsiElement child = codeBlock.getLastChild(); child != null; child = child.getPrevSibling()) {
      if (child instanceof PsiStatement) {
        return (PsiStatement)child;
      }
    }
    return null;
  }

  /**
   * @return null, if zero or more than one statements in the specified code block.
   */
  public static @Nullable PsiStatement getOnlyStatementInBlock(@Nullable PsiCodeBlock codeBlock) {
    return getOnlyChildOfType(codeBlock, PsiStatement.class);
  }

  static <T extends PsiElement> T getOnlyChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
    if (element == null) return null;
    T result = null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        if (result == null) {
          //noinspection unchecked
          result = (T)child;
        }
        else {
          return null;
        }
      }
    }
    return result;
  }

  public static boolean hasStatementCount(@Nullable PsiCodeBlock codeBlock, int count) {
    return hasChildrenOfTypeCount(codeBlock, count, PsiStatement.class);
  }

  public static <T extends PsiElement> boolean hasChildrenOfTypeCount(@Nullable PsiElement element, int count, @NotNull Class<T> aClass) {
    if (element == null) return false;
    int i = 0;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (aClass.isInstance(child)) {
        i++;
        if (i > count) return false;
      }
    }
    return i == count;
  }

  public static <T extends PsiElement> boolean isNestedElement(@NotNull T element, @NotNull Class<? extends T> aClass) {
    return PsiTreeUtil.getParentOfType(element, aClass, true, PsiClass.class, PsiLambdaExpression.class) != null;
  }

  public static boolean isEmptyCodeBlock(PsiCodeBlock codeBlock) {
    return hasStatementCount(codeBlock, 0);
  }

  public static boolean methodAlwaysThrowsException(@NotNull PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return true;
    }
    return !containsReturn(body) && !codeBlockMayCompleteNormally(body);
  }

  public static boolean lambdaExpressionAlwaysThrowsException(PsiLambdaExpression expression) {
    final PsiElement body = expression.getBody();
    if (body instanceof PsiExpression) {
      return false;
    }
    return !(body instanceof PsiCodeBlock codeBlock) || !containsReturn(codeBlock) && !codeBlockMayCompleteNormally(codeBlock);
  }

  /**
   * @param statement statement to test
   * @return true if statement contains a break without a label that could jump outside of the supplied statement
   */
  @Contract("null -> false")
  public static boolean statementContainsNakedBreak(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    final NakedBreakFinder breakFinder = new NakedBreakFinder();
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  /**
   * @param statement statement to test
   * @return true if statement contains a continue without a label that could jump outside of the supplied statement
   */
  @Contract("null -> false")
  public static boolean statementContainsNakedContinue(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    final NakedContinueFinder breakFinder = new NakedContinueFinder();
    statement.accept(breakFinder);
    return breakFinder.continueFound();
  }

  /**
   * Checks whether the given statement effectively breaks given loop. Returns true
   * if the statement is {@link PsiBreakStatement} having given loop as a target. Also may return
   * true in other cases if the statement is semantically equivalent to break like this:
   *
   * <pre>{@code
   * int myMethod(int[] data) {
   *   for(int val : data) {
   *     if(val == 5) {
   *       System.out.println(val);
   *       return 0; // this statement is semantically equivalent to break.
   *     }
   *   }
   *   return 0;
   * }}</pre>
   *
   * @param statement statement which may break the loop
   * @param loop a loop to break
   * @return true if the statement actually breaks the loop
   */
  @Contract("null, _ -> false")
  public static boolean statementBreaksLoop(PsiStatement statement, PsiLoopStatement loop) {
    if(statement instanceof PsiBreakStatement breakStatement) {
      return breakStatement.findExitedStatement() == loop;
    }
    if(statement instanceof PsiReturnStatement returnStatement) {
      PsiExpression returnValue = returnStatement.getReturnValue();
      PsiElement cur = loop;
      for(PsiElement parent = cur.getParent();;parent = cur.getParent()) {
        if(parent instanceof PsiLabeledStatement) {
          cur = parent;
        } else if(parent instanceof PsiCodeBlock block) {
          PsiStatement[] statements = block.getStatements();
          if(block.getParent() instanceof PsiBlockStatement && statements.length > 0 && statements[statements.length-1] == cur) {
            cur = block.getParent();
          } else break;
        } else if(parent instanceof PsiIfStatement ifStatement) {
          if(cur == ifStatement.getThenBranch() || cur == ifStatement.getElseBranch()) {
            cur = parent;
          } else break;
        } else break;
      }
      PsiElement nextElement = PsiTreeUtil.skipWhitespacesAndCommentsForward(cur);
      if(nextElement instanceof PsiReturnStatement nextReturn) {
        return EquivalenceChecker.getCanonicalPsiEquivalence()
          .expressionsAreEquivalent(returnValue, nextReturn.getReturnValue());
      }
      return returnValue == null &&
             cur.getParent() instanceof PsiCodeBlock block &&
             block.getParent() instanceof PsiMethod &&
             PsiUtil.isJavaToken(nextElement, JavaTokenType.RBRACE);
    }
    return false;
  }

  private static StreamEx<PsiExpression> conditions(PsiElement element) {
    return StreamEx.iterate(element, e -> e != null &&
                                          !(e instanceof PsiLambdaExpression) && !(e instanceof PsiMethod), PsiElement::getParent)
      .pairMap((child, parent) -> parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getThenBranch() == child ? parent : null)
      .select(PsiIfStatement.class)
      .map(PsiIfStatement::getCondition)
      .flatMap(cond -> cond instanceof PsiPolyadicExpression && ((PsiPolyadicExpression)cond).getOperationTokenType().equals(
        JavaTokenType.ANDAND) ? StreamEx.of(((PsiPolyadicExpression)cond).getOperands()) : StreamEx.of(cond));
  }

  /**
   * @param statement statement to check
   * @param loop      surrounding loop
   * @return true if it could be statically determined that given statement is executed at most once
   */
  public static boolean isExecutedOnceInLoop(PsiStatement statement, PsiLoopStatement loop) {
    if (flowBreaksLoop(statement, loop)) return true;
    if (loop instanceof PsiForStatement forLoop) {
      // Check that we're inside counted loop which increments some loop variable and
      // the code is executed under condition like if(var == something)
      PsiDeclarationStatement initialization = ObjectUtils.tryCast(forLoop.getInitialization(), PsiDeclarationStatement.class);
      PsiStatement update = forLoop.getUpdate();
      if (initialization != null && update != null) {
        PsiLocalVariable variable = StreamEx.of(initialization.getDeclaredElements()).select(PsiLocalVariable.class)
          .findFirst(var -> LoopDirection.evaluateLoopDirection(var, update) != null).orElse(null);
        if (variable != null) {
          boolean hasLoopVarCheck = conditions(statement).select(PsiBinaryExpression.class)
            .filter(binOp -> binOp.getOperationTokenType().equals(JavaTokenType.EQEQ))
            .anyMatch(binOp -> ExpressionUtils.getOtherOperand(binOp, variable) != null);
          if (hasLoopVarCheck) {
            return ContainerUtil.and(VariableAccessUtils.getVariableReferences(variable), expression -> 
              PsiTreeUtil.isAncestor(update, expression, false) || !PsiUtil.isAccessedForWriting(expression));
          }
        }
      }
    }
    return false;
  }

  /**
   * Returns true if the variable is definitely reassigned to fresh value after executing given statement
   * without intermediate usages (ignoring possible exceptions in-between)
   *
   * @param statement statement to start checking from
   * @param variable variable to check
   * @return true if variable is reassigned
   */
  public static boolean isVariableReassigned(PsiStatement statement, PsiVariable variable) {
    for (PsiStatement sibling = nextExecutedStatement(statement); sibling != null; sibling = nextExecutedStatement(sibling)) {
      PsiExpression rValue = ExpressionUtils.getAssignmentTo(sibling, variable);
      if (rValue != null && !VariableAccessUtils.variableIsUsed(variable, rValue)) return true;
      if (VariableAccessUtils.variableIsUsed(variable, sibling)) return false;
    }
    return false;
  }

  /**
   * Checks whether control flow after executing given statement will definitely not go into the next iteration of given loop.
   *
   * @param statement executed statement. It's not checked whether this statement itself breaks the loop.
   * @param loop a surrounding loop. Must be parent of statement
   * @return true if it can be statically defined that next loop iteration will not be executed.
   */
  @Contract("null, _ -> false")
  public static boolean flowBreaksLoop(PsiStatement statement, PsiLoopStatement loop) {
    if(statement == null || statement == loop) return false;
    for (PsiStatement sibling = statement; sibling != null; sibling = nextExecutedStatement(sibling)) {
      if(sibling instanceof PsiContinueStatement continueStatement) {
        PsiStatement continueTarget = continueStatement.findContinuedStatement();
        return PsiTreeUtil.isAncestor(continueTarget, loop, true);
      }
      if(sibling instanceof PsiThrowStatement || sibling instanceof PsiReturnStatement) return true;
      if(sibling instanceof PsiBreakStatement breakStatement) {
        PsiStatement exitedStatement = breakStatement.findExitedStatement();
        if(exitedStatement == loop) return true;
        return flowBreaksLoop(nextExecutedStatement(exitedStatement), loop);
      }
      if (sibling instanceof PsiIfStatement || sibling instanceof PsiSwitchStatement) {
        if (!PsiTreeUtil.collectElementsOfType(sibling, PsiContinueStatement.class).isEmpty()) return false;
      }
      if (sibling instanceof PsiLoopStatement) {
        if (PsiTreeUtil.collectElements(sibling, e -> e instanceof PsiContinueStatement continueStatement &&
                                                      continueStatement.getLabelIdentifier() != null).length > 0) {
          return false;
        }
      }
    }
    return false;
  }

  private static @Nullable PsiStatement firstStatement(@Nullable PsiStatement statement) {
    while (statement instanceof PsiBlockStatement) {
      PsiStatement[] statements = ((PsiBlockStatement)statement).getCodeBlock().getStatements();
      if (statements.length == 0) break;
      statement = statements[0];
    }
    return statement;
  }

  @Contract("null -> null")
  private static @Nullable PsiStatement nextExecutedStatement(PsiStatement statement) {
    if (statement == null) return null;
    PsiStatement next = firstStatement(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class));
    if (next == null) {
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiCodeBlock) {
        PsiElement gParent = parent.getParent();
        if (gParent instanceof PsiBlockStatement || gParent instanceof PsiSwitchStatement) {
          return nextExecutedStatement((PsiStatement)gParent);
        }
      }
      else if (parent instanceof PsiLabeledStatement || parent instanceof PsiIfStatement || parent instanceof PsiSwitchLabelStatement
               || parent instanceof PsiSwitchStatement) {
        return nextExecutedStatement((PsiStatement)parent);
      }
    }
    return next;
  }

  /**
   * Checks whether variable can be referenced between start and given statement entry.
   * Back-edges are also considered, so the actual place where it referenced might be outside of
   * (start, loop entry) interval.
   *
   * @param flow      ControlFlow to analyze
   * @param start     start point
   * @param statement statement to check
   * @param variable  variable to analyze
   * @param excluded  instructions to exclude
   * @return true if variable can be referenced between start point and statement entry
   */
  public static boolean isVariableReferencedBeforeStatementEntry(@NotNull ControlFlow flow,
                                                                 final int start,
                                                                 final PsiElement statement,
                                                                 @NotNull PsiVariable variable,
                                                                 @NotNull @Unmodifiable Set<Integer> excluded) {
    final int statementStart = flow.getStartOffset(statement);
    final int statementEnd = flow.getEndOffset(statement);

    List<ControlFlowUtil.ControlFlowEdge> edges = ControlFlowUtil.getEdges(flow, start);
    // DFS visits instructions mainly in backward direction while here visiting in forward direction
    // greatly reduces number of iterations.
    Collections.reverse(edges);

    BitSet referenced = new BitSet();
    boolean changed = true;
    while(changed) {
      changed = false;
      for(ControlFlowUtil.ControlFlowEdge edge: edges) {
        int from = edge.myFrom;
        int to = edge.myTo;
        if(referenced.get(from)) {
          // jump to the loop start from within the loop is not considered as loop entry
          if (to == statementStart && (from < statementStart || from >= statementEnd)) {
            return true;
          }
          if (!referenced.get(to) && !excluded.contains(to)) {
            referenced.set(to);
            changed = true;
          }
          continue;
        }
        if (ControlFlowUtil.isVariableAccess(flow, from, variable) && !excluded.contains(from)) {
          referenced.set(from);
          referenced.set(to);
          if (to == statementStart) return true;
          changed = true;
        }
      }
    }
    return false;
  }

  /**
   * Returns an {@link InitializerUsageStatus} for given variable with respect to given statement
   * @param var a variable to determine an initializer usage status for
   * @param statement a statement where variable is used
   * @return initializer usage status for variable
   */
  public static @NotNull InitializerUsageStatus getInitializerUsageStatus(PsiVariable var, PsiStatement statement) {
    if(!(var instanceof PsiLocalVariable) || var.getInitializer() == null) return UNKNOWN;
    if(isDeclarationJustBefore(var, statement)) return DECLARED_JUST_BEFORE;
    // Check that variable is declared in the same method or the same lambda expression
    if(PsiTreeUtil.getParentOfType(var, PsiLambdaExpression.class, PsiMethod.class) !=
       PsiTreeUtil.getParentOfType(statement, PsiLambdaExpression.class, PsiMethod.class)) return UNKNOWN;
    PsiElement block = PsiUtil.getVariableCodeBlock(var, null);
    if(block == null) return UNKNOWN;
    final ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(statement.getProject())
        .getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    }
    catch (AnalysisCanceledException ignored) {
      return UNKNOWN;
    }
    int start = controlFlow.getEndOffset(var.getInitializer())+1;
    int stop = controlFlow.getStartOffset(statement);
    if (isVariableReferencedBeforeStatementEntry(controlFlow, start, statement, var, Collections.emptySet())) return UNKNOWN;
    if (!ControlFlowUtil.isValueUsedWithoutVisitingStop(controlFlow, start, stop, var)) return AT_WANTED_PLACE_ONLY;
    return var.hasModifierProperty(PsiModifier.FINAL) ? UNKNOWN : AT_WANTED_PLACE;
  }

  private static boolean isDeclarationJustBefore(PsiVariable var, PsiStatement nextStatement) {
    PsiElement declaration = var.getParent();
    PsiElement nextStatementParent = nextStatement.getParent();
    if(nextStatementParent instanceof PsiLabeledStatement) {
      nextStatement = (PsiStatement)nextStatementParent;
    }
    if(declaration instanceof PsiDeclarationStatement) {
      PsiElement[] elements = ((PsiDeclarationStatement)declaration).getDeclaredElements();
      if (ArrayUtil.getLastElement(elements) == var && nextStatement.equals(
        PsiTreeUtil.skipWhitespacesAndCommentsForward(declaration))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if statement essentially contains no executable code
   *
   * @param statement statement to test
   * @return true if statement essentially contains no executable code
   */
  @Contract("null -> false")
  public static boolean statementIsEmpty(PsiStatement statement) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof PsiEmptyStatement) {
      return true;
    }
    if (statement instanceof final PsiBlockStatement blockStatement) {
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiStatement[] codeBlockStatements = codeBlock.getStatements();
      for (PsiStatement codeBlockStatement : codeBlockStatements) {
        if (!statementIsEmpty(codeBlockStatement)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Finds the return statement which will be always executed after the supplied statement. It supports constructs like this:
   * <pre>{@code
   * if(condition) {
   *   statement(); // this statement is supplied as a parameter
   * }
   * return true; // this return statement will be returned
   * }</pre>
   *
   * @param statement statement to find the return after
   * @return the found return statement or null.
   */
  public static @Nullable PsiReturnStatement getNextReturnStatement(PsiStatement statement) {
    while (true) {
      PsiElement nextStatement = PsiTreeUtil.skipWhitespacesAndCommentsForward(statement);
      if (nextStatement instanceof PsiReturnStatement) return (PsiReturnStatement)nextStatement;
      PsiElement parent = statement.getParent();
      if (parent instanceof PsiCodeBlock) {
        PsiStatement[] statements = ((PsiCodeBlock)parent).getStatements();
        if (statements.length == 0 || statements[statements.length - 1] != statement) return null;
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiIfStatement) && !(parent instanceof PsiBlockStatement)) return null;
      statement = (PsiStatement)parent;
    }
  }

  /**
   * @param statement statement to test
   * @return true if statement is reachable or code is incomplete and reachability cannot be defined
   */
  public static boolean isReachable(@NotNull PsiStatement statement) {
    ControlFlow flow;
    PsiElement block = statement;
    do {
      block = PsiTreeUtil.getParentOfType(block, PsiCodeBlock.class);
      if (block == null) return true;
    }
    while (block.getParent() instanceof PsiSwitchStatement);
    try {
      flow = ControlFlowFactory.getInstance(statement.getProject())
        .getControlFlow(block, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
    return ControlFlowUtil.isInstructionReachable(flow, flow.getStartOffset(statement), 0);
  }

  /**
   * Returns true if given element is an empty statement
   *
   * @param element element to check
   * @param commentIsContent if true, empty statement containing comments is not considered empty
   * @param emptyBlocks if true, empty block (or nested empty block like {@code {{}}}) is considered an empty statement
   * @return true if given element is an empty statement
   */
  public static boolean isEmpty(PsiElement element, boolean commentIsContent, boolean emptyBlocks) {
    if (!commentIsContent && element instanceof PsiComment) {
      return true;
    }
    else if (element instanceof PsiEmptyStatement) {
      return !commentIsContent ||
             PsiTreeUtil.getChildOfType(element, PsiComment.class) == null &&
             !(PsiTreeUtil.skipWhitespacesBackward(element) instanceof PsiComment);
    }
    else if (element instanceof PsiWhiteSpace) {
      return true;
    }
    else if (element instanceof final PsiBlockStatement block) {
      return isEmpty(block.getCodeBlock(), commentIsContent, emptyBlocks);
    }
    else if (emptyBlocks && element instanceof final PsiCodeBlock codeBlock) {
      final PsiElement[] children = codeBlock.getChildren();
      if (children.length == 2) {
        return true;
      }
      for (int i = 1; i < children.length - 1; i++) {
        final PsiElement child = children[i];
        if (!isEmpty(child, commentIsContent, true)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Ensures that the {@code if} statement has the {@code else} branch which is a block statement (adding it if absent)
   * @param ifStatement an {@code if} statement to add an else branch or expand it to the block
   */
  public static void ensureElseBranch(PsiIfStatement ifStatement) {
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      if (!(elseBranch instanceof PsiBlockStatement)) {
        BlockUtils.expandSingleStatementToBlockStatement(elseBranch);
      }
    } else {
      PsiStatement thenBranch = ifStatement.getThenBranch();
      PsiBlockStatement emptyBlock = BlockUtils.createBlockStatement(ifStatement.getProject());
      if (thenBranch == null) {
        ifStatement.setThenBranch(emptyBlock);
      } else if (!(thenBranch instanceof PsiBlockStatement)) {
        BlockUtils.expandSingleStatementToBlockStatement(thenBranch);
      }
      ifStatement.setElseBranch(emptyBlock);
    }
  }

  public enum InitializerUsageStatus {
    // Variable is declared just before the wanted place
    DECLARED_JUST_BEFORE,
    // All initial value usages go through wanted place and at wanted place the variable value is guaranteed to be the initial value
    AT_WANTED_PLACE_ONLY,
    // At wanted place the variable value is guaranteed to have the initial value, but this initial value might be used somewhere else
    AT_WANTED_PLACE,
    // It's not guaranteed that the variable value at wanted place is initial value
    UNKNOWN
  }

  private static class NakedBreakFinder extends JavaRecursiveElementWalkingVisitor {
    private boolean m_found;

    private boolean breakFound() {
      return m_found;
    }

    @Override
    public void visitExpression(@NotNull PsiExpression expression) {
      // don't drill down
    }

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      if (statement.getLabelIdentifier() != null) {
        return;
      }
      m_found = true;
      stopWalking();
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForeachPatternStatement(@NotNull PsiForeachPatternStatement statement) {
      // don't drill down
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      // don't drill down
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      // don't drill down
    }
  }

  private static class NakedContinueFinder extends JavaRecursiveElementWalkingVisitor {
    private boolean m_found;

    private boolean continueFound() {
      return m_found;
    }

    @Override
    public void visitExpression(@NotNull PsiExpression expression) {
      // don't drill down
    }

    @Override
    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      if (statement.getLabelIdentifier() != null) {
        return;
      }
      m_found = true;
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      // don't drill down
    }

    @Override
    public void visitForeachPatternStatement(@NotNull PsiForeachPatternStatement statement) {
      // don't drill down
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      // don't drill down
    }
  }

  private static class SystemExitFinder extends JavaRecursiveElementWalkingVisitor {

    private boolean m_found;

    private boolean exitFound() {
      return m_found;
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // do nothing to keep from drilling into inner classes
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      if (m_found) {
        return;
      }
      super.visitMethodCallExpression(expression);
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final @NonNls String methodName = method.getName();
      if (!methodName.equals("exit")) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!"java.lang.System".equals(className) && !"java.lang.Runtime".equals(className)) {
        return;
      }
      m_found = true;
    }
  }

  private static class ReturnFinder extends JavaRecursiveElementWalkingVisitor {
    private boolean myFound;

    private boolean returnFound() {
      return myFound;
    }

    @Override
    public void visitClass(@NotNull PsiClass psiClass) {
      // do nothing, to keep drilling into inner classes
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement returnStatement) {
      myFound = true;
      stopWalking();
    }
  }

  private static class YieldFinder extends JavaRecursiveElementWalkingVisitor {
    private boolean myFound;

    private boolean yieldFound() {
      return myFound;
    }

    @Override
    public void visitExpression(@NotNull PsiExpression expression) {
      // don't drill down
    }

    @Override
    public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
      myFound = true;
      stopWalking();
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      if (myFound) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final Object value = ExpressionUtils.computeConstantExpression(condition);
      if (Boolean.FALSE != value) {
        final PsiStatement thenBranch = statement.getThenBranch();
        if (thenBranch != null) {
          thenBranch.accept(this);
        }
      }
      if (Boolean.TRUE != value) {
        final PsiStatement elseBranch = statement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.accept(this);
        }
      }
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      // don't drill down
    }
  }


  private static class BreakFinder extends JavaRecursiveElementWalkingVisitor {

    private boolean m_found;
    private final PsiStatement m_target;
    private final boolean myAncestor;

    BreakFinder(@NotNull PsiStatement target, boolean ancestor) {
      m_target = target;
      myAncestor = ancestor;
    }

    boolean breakFound() {
      return m_found;
    }

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      if (m_found) {
        return;
      }
      super.visitBreakStatement(statement);
      final PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement == null) {
        return;
      }
      if (myAncestor) {
        if (PsiTreeUtil.isAncestor(exitedStatement, m_target, false)) {
          m_found = true;
        }
      } else if (exitedStatement == m_target) {
        m_found = true;
      }
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      if (m_found) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final Object value = ExpressionUtils.computeConstantExpression(condition);
      if (Boolean.FALSE != value) {
        final PsiStatement thenBranch = statement.getThenBranch();
        if (thenBranch != null) {
          thenBranch.accept(this);
        }
      }
      if (Boolean.TRUE != value) {
        final PsiStatement elseBranch = statement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.accept(this);
        }
      }
    }
  }

  private static final class ContinueFinder extends JavaRecursiveElementWalkingVisitor {

    private boolean m_found;
    private final PsiStatement m_target;

    private ContinueFinder(@NotNull PsiStatement target) {
      m_target = target;
    }

    private boolean continueFound() {
      return m_found;
    }

    @Override
    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      if (m_found) {
        return;
      }
      super.visitContinueStatement(statement);
      final PsiStatement continuedStatement = statement.findContinuedStatement();
      if (continuedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(continuedStatement, m_target, false)) {
        m_found = true;
      }
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      if (m_found) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final Object value = ExpressionUtils.computeConstantExpression(condition);
      if (Boolean.FALSE != value) {
        final PsiStatement thenBranch = statement.getThenBranch();
        if (thenBranch != null) {
          thenBranch.accept(this);
        }
      }
      if (Boolean.TRUE != value) {
        final PsiStatement elseBranch = statement.getElseBranch();
        if (elseBranch != null) {
          elseBranch.accept(this);
        }
      }
    }
  }

  private static final class MethodCallFinder extends JavaRecursiveElementWalkingVisitor {

    private final String containingClassName;
    private final PsiType returnType;
    private final String methodName;
    private final PsiType[] parameterTypeNames;
    private boolean containsCallToMethod;

    private MethodCallFinder(String containingClassName, PsiType returnType, String methodName, PsiType... parameterTypeNames) {
      this.containingClassName = containingClassName;
      this.returnType = returnType;
      this.methodName = methodName;
      this.parameterTypeNames = parameterTypeNames;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (containsCallToMethod) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      if (containsCallToMethod) {
        return;
      }
      super.visitMethodCallExpression(expression);
      if (!MethodCallUtils.isCallToMethod(expression, containingClassName, returnType, methodName, parameterTypeNames)) {
        return;
      }
      containsCallToMethod = true;
    }

    private boolean containsCallToMethod() {
      return containsCallToMethod;
    }
  }

  private static final class ContinueToAncestorFinder extends JavaRecursiveElementWalkingVisitor {

    private final PsiStatement statement;
    private boolean found;

    private ContinueToAncestorFinder(PsiStatement statement) {
      this.statement = statement;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (found) {
        return;
      }
      super.visitElement(element);
    }

    @Override
    public void visitContinueStatement(
      @NotNull PsiContinueStatement continueStatement) {
      if (found) {
        return;
      }
      super.visitContinueStatement(continueStatement);
      final PsiIdentifier labelIdentifier = continueStatement.getLabelIdentifier();
      if (labelIdentifier == null) {
        return;
      }
      final PsiStatement continuedStatement = continueStatement.findContinuedStatement();
      if (continuedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(continuedStatement, statement, true)) {
        found = true;
      }
    }

    private boolean continueToAncestorFound() {
      return found;
    }
  }

  /**
   * Processes elements in the current scope (not in lambda, nested classes on so on) using a given processor.
   *
   * @param context the context element to start the processing from
   * @param processor the processor to be used for element processing
   */
  public static void processElementsInCurrentScope(@NotNull PsiElement context,
                                                   @NotNull Processor<? super @NotNull PsiElement> processor) {
    class Visitor extends JavaRecursiveElementWalkingVisitor {
      boolean stop = false;

      @Override
      public void visitElement(@NotNull PsiElement psiElement) {
        if (psiElement != context) {
          PsiElement parent = psiElement.getParent();
          // do not process any anonymous class children except its getArgumentList()
          //if the visitor was not called from inside anonymous class
          if (parent instanceof PsiAnonymousClass && !(psiElement instanceof PsiExpressionList)) {
            return;
          }
        }
        stop = !processor.process(psiElement);
        if (stop) {
          return;
        }
        super.visitElement(psiElement);
      }

      @Override
      public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
        // process anonymous getArgumentList()
        visitElement(aClass);
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
      }
    }

    Visitor visitor = new Visitor();
    context.accept(visitor);
  }
}
