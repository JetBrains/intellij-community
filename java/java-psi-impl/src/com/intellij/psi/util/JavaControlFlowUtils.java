// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaControlFlowUtils {
  private JavaControlFlowUtils() { }

  public static boolean statementMayCompleteNormally(@Nullable PsiStatement statement) {
    if (statement == null) {
      return true;
    }
    if (statement instanceof PsiBreakStatement || statement instanceof PsiContinueStatement || statement instanceof PsiYieldStatement ||
        statement instanceof PsiReturnStatement || statement instanceof PsiThrowStatement) {
      return false;
    }
    else if (statement instanceof PsiExpressionListStatement || statement instanceof PsiEmptyStatement ||
             statement instanceof PsiAssertStatement || statement instanceof PsiDeclarationStatement ||
             statement instanceof PsiSwitchLabelStatement || statement instanceof PsiForeachStatement) {
      return true;
    }
    else if (statement instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return true;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return true;
      }
      @NonNls final String methodName = method.getName();
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
    else if (statement instanceof PsiForStatement) {
      return forStatementMayCompleteNormally((PsiForStatement)statement);
    }
    else if (statement instanceof PsiWhileStatement) {
      return whileStatementMayCompleteNormally((PsiWhileStatement)statement);
    }
    else if (statement instanceof PsiDoWhileStatement) {
      return doWhileStatementMayCompleteNormally((PsiDoWhileStatement)statement);
    }
    else if (statement instanceof PsiSynchronizedStatement) {
      final PsiCodeBlock body = ((PsiSynchronizedStatement)statement).getBody();
      return codeBlockMayCompleteNormally(body);
    }
    else if (statement instanceof PsiBlockStatement) {
      final PsiCodeBlock codeBlock = ((PsiBlockStatement)statement).getCodeBlock();
      return codeBlockMayCompleteNormally(codeBlock);
    }
    else if (statement instanceof PsiLabeledStatement) {
      return labeledStatementMayCompleteNormally((PsiLabeledStatement)statement);
    }
    else if (statement instanceof PsiIfStatement) {
      return ifStatementMayCompleteNormally((PsiIfStatement)statement);
    }
    else if (statement instanceof PsiTryStatement) {
      return tryStatementMayCompleteNormally((PsiTryStatement)statement);
    }
    else if (statement instanceof PsiSwitchStatement) {
      return switchStatementMayCompleteNormally((PsiSwitchStatement)statement);
    }
    else if (statement instanceof PsiSwitchLabeledRuleStatement) {
      PsiStatement body = ((PsiSwitchLabeledRuleStatement)statement).getBody();
      return body != null && statementMayCompleteNormally(body);
    }
    else if (statement instanceof PsiTemplateStatement || statement instanceof PsiClassLevelDeclarationStatement) {
      return true;
    }
    else {
      assert false : "unknown statement type: " + statement.getClass();
      return true;
    }
  }

  private static boolean doWhileStatementMayCompleteNormally(@NotNull PsiDoWhileStatement loopStatement) {
    final PsiExpression condition = loopStatement.getCondition();
    final Object value = JavaExpressionUtils.computeConstantExpression(condition);
    final PsiStatement body = loopStatement.getBody();
    return statementMayCompleteNormally(body) && value != Boolean.TRUE
           || statementContainsBreakToStatementOrAncestor(loopStatement) || statementContainsContinueToAncestor(loopStatement);
  }

  private static boolean whileStatementMayCompleteNormally(@NotNull PsiWhileStatement loopStatement) {
    final PsiExpression condition = loopStatement.getCondition();
    final Object value = JavaExpressionUtils.computeConstantExpression(condition);
    return value != Boolean.TRUE || statementContainsBreakToStatementOrAncestor(loopStatement) || statementContainsContinueToAncestor(loopStatement);
  }

  private static boolean forStatementMayCompleteNormally(@NotNull PsiForStatement loopStatement) {
    if (statementContainsBreakToStatementOrAncestor(loopStatement)) {
      return true;
    }
    if (statementContainsContinueToAncestor(loopStatement)) {
      return true;
    }
    final PsiExpression condition = loopStatement.getCondition();
    if (condition == null) {
      return false;
    }
    final Object value = JavaExpressionUtils.computeConstantExpression(condition);
    return Boolean.TRUE != value;
  }

  private static boolean switchStatementMayCompleteNormally(@NotNull PsiSwitchStatement switchStatement) {
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
    boolean hasDefaultCase = false, hasTotalPattern = false;
    for (PsiStatement statement : statements) {
      if (statement instanceof PsiSwitchLabelStatementBase) {
        if (statement instanceof PsiSwitchLabelStatement) {
          numCases++;
        }
        if (hasDefaultCase || hasTotalPattern) {
          continue;
        }
        PsiSwitchLabelStatementBase switchLabelStatement = (PsiSwitchLabelStatementBase)statement;
        if (switchLabelStatement.isDefaultCase()) {
          hasDefaultCase = true;
          continue;
        }
        // this information doesn't exist in spec draft (14.22) for pattern in switch as expected
        // but for now javac considers the switch statement containing at least either case default label element or total pattern "incomplete normally"
        if (PsiUtil.getLanguageLevel(switchLabelStatement).isAtLeast(LanguageLevel.JDK_17_PREVIEW)) {
          PsiCaseLabelElementList labelElementList = switchLabelStatement.getCaseLabelElementList();
          if (labelElementList == null) {
            continue;
          }
          for (PsiCaseLabelElement labelElement : labelElementList.getElements()) {
            if (labelElement instanceof PsiDefaultCaseLabelElement) {
              hasDefaultCase = true;
            }
            else if (labelElement instanceof PsiPattern) {
              hasTotalPattern = JavaPsiPatternUtil.isTotalForType(((PsiPattern)labelElement), selectorType);
            }
          }
        }
      }
      else if (statement instanceof PsiBreakStatement) {
        final PsiBreakStatement breakStatement = (PsiBreakStatement)statement;
        if (breakStatement.getLabelIdentifier() == null) {
          return true;
        }
      }
    }
    // actually there is no information about an impact of enum constants on switch statements being complete normally in spec (Unreachable statements)
    // comparing to javac that produces some false-negative highlighting in enum switch statements containing all possible constants
    final boolean isEnum = isEnumSwitch(switchStatement);
    if (!hasDefaultCase && !hasTotalPattern && !isEnum) {
      return true;
    }
    if (!hasDefaultCase && !hasTotalPattern) {
      final PsiClass aClass = ((PsiClassType)selectorType).resolve();
      if (aClass == null) {
        return true;
      }
      if (!hasChildrenOfTypeCount(aClass, numCases, PsiEnumConstant.class)) {
        return true;
      }
    }
    // 14.22. Unreachable Statements
    // We need to check every rule's body not just the last one if the switch block includes the switch rules
    boolean isLabeledRuleSwitch = statements[0] instanceof PsiSwitchLabeledRuleStatement;
    if (isLabeledRuleSwitch) {
      for (PsiStatement statement : statements) {
        if (statementMayCompleteNormally(statement)) {
          return true;
        }
      }
      return false;
    }
    return statementMayCompleteNormally(statements[statements.length - 1]);
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

  private static boolean tryStatementMayCompleteNormally(@NotNull PsiTryStatement tryStatement) {
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      if (!codeBlockMayCompleteNormally(finallyBlock)) {
        return false;
      }
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (codeBlockMayCompleteNormally(tryBlock)) {
      return true;
    }
    final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
    for (final PsiCodeBlock catchBlock : catchBlocks) {
      if (codeBlockMayCompleteNormally(catchBlock)) {
        return true;
      }
    }
    return false;
  }

  private static boolean ifStatementMayCompleteNormally(@NotNull PsiIfStatement ifStatement) {
    final PsiExpression condition = ifStatement.getCondition();
    final Object value = JavaExpressionUtils.computeConstantExpression(condition);
    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (value == Boolean.TRUE) {
      return statementMayCompleteNormally(thenBranch);
    }
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    if (value == Boolean.FALSE) {
      return statementMayCompleteNormally(elseBranch);
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
    return statementMayCompleteNormally(branch1) || statementMayCompleteNormally(branch2);
  }

  private static boolean labeledStatementMayCompleteNormally(@NotNull PsiLabeledStatement labeledStatement) {
    final PsiStatement statement = labeledStatement.getStatement();
    if (statement == null) {
      return false;
    }
    return statementMayCompleteNormally(statement) || statementContainsBreakToStatementOrAncestor(statement);
  }

  public static boolean codeBlockMayCompleteNormally(@Nullable PsiCodeBlock block) {
    if (block == null) {
      return true;
    }
    final PsiStatement[] statements = block.getStatements();
    for (final PsiStatement statement : statements) {
      if (!statementMayCompleteNormally(statement)) {
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
    public void visitIfStatement(PsiIfStatement statement) {
      if (m_found) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final Object value = JavaExpressionUtils.computeConstantExpression(condition);
      if (value == null) return;

      if (Boolean.TRUE == value) {
        final PsiStatement elseBranch = statement.getElseBranch();
        if (elseBranch != null) elseBranch.accept(this);
      }
      else if (Boolean.FALSE == value) {
        final PsiStatement thenBranch = statement.getThenBranch();
        if (thenBranch != null) thenBranch.accept(this);
      }
    }
  }

  public static final class ContinueToAncestorFinder extends JavaRecursiveElementWalkingVisitor {

    private final PsiStatement statement;
    private boolean found;

    public ContinueToAncestorFinder(PsiStatement statement) {
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
      PsiContinueStatement continueStatement) {
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
}