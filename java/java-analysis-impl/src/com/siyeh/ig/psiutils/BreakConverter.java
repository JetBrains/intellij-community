// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A class which converts switch statement breaks before switch statement
 * is unwrapped.
 */
public class BreakConverter {
  private final PsiSwitchBlock mySwitchBlock;
  private final String myReplacement;

  public BreakConverter(PsiSwitchBlock switchBlock, String replacement) {
    mySwitchBlock = switchBlock;
    myReplacement = replacement;
  }

  public void process() {
    List<PsiBreakStatement> breaks = collectBreaks();
    Map<Boolean, List<PsiBreakStatement>> groups =
      StreamEx.of(breaks).partitioningBy(breakStatement -> isRemovable(mySwitchBlock, breakStatement));
    List<PsiBreakStatement> removableBreaks = groups.get(true);
    removableBreaks.forEach(DeleteUnnecessaryStatementFix::deleteUnnecessaryStatement);
    List<PsiBreakStatement> replaceableBreaks = groups.get(false);
    if (!replaceableBreaks.isEmpty()) {
      assert myReplacement != null;
      for (PsiBreakStatement breakStatement : replaceableBreaks) {
        new CommentTracker().replaceAndRestoreComments(breakStatement, myReplacement);
      }
    }
  }

  private @NotNull List<PsiBreakStatement> collectBreaks() {
    List<PsiBreakStatement> breaks = new ArrayList<>();
    mySwitchBlock.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
        super.visitBreakStatement(statement);
        if (statement.findExitedStatement() == mySwitchBlock) {
          breaks.add(statement);
        }
      }

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        // Going down into any expression seems redundant
      }

      @Override
      public void visitClass(@NotNull PsiClass aClass) {}
    });
    return breaks;
  }

  private static @Nullable @NonNls String getReplacement(PsiStatement statement) {
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiIfStatement || parent instanceof PsiLabeledStatement) {
      return getReplacement((PsiStatement)parent);
    }
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (nextStatement != null) {
      if (nextStatement instanceof PsiContinueStatement ||
          nextStatement instanceof PsiBreakStatement ||
          nextStatement instanceof PsiReturnStatement ||
          nextStatement instanceof PsiThrowStatement) {
        return nextStatement.getText();
      }
      return null;
    }
    if (parent == null) return null;
    if (parent instanceof PsiLoopStatement) {
      return "continue;";
    }
    if (parent instanceof PsiCodeBlock) {
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethod && PsiTypes.voidType().equals(((PsiMethod)grandParent).getReturnType()) ||
          grandParent instanceof PsiLambdaExpression &&
          PsiTypes.voidType().equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiFunctionalExpression)grandParent))) {
        return "return;";
      }
      if (grandParent instanceof PsiBlockStatement) {
        return getReplacement((PsiStatement)grandParent);
      }
    }
    return null;
  }

  private static boolean isRemovable(PsiSwitchBlock switchStatement, PsiStatement statement) {
    if (switchStatement instanceof PsiSwitchExpression) {
      // any breaks inside switch expressions are not convertible
      return false;
    }
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiSwitchLabeledRuleStatement) {
      return true;
    }
    if (parent instanceof PsiIfStatement || parent instanceof PsiLabeledStatement) {
      return isRemovable(switchStatement, (PsiStatement)parent);
    }
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (nextStatement == null) {
      if (parent instanceof PsiCodeBlock) {
        PsiElement grandParent = parent.getParent();
        return grandParent == switchStatement ||
               grandParent instanceof PsiBlockStatement && isRemovable(switchStatement, (PsiStatement)grandParent);
      }
    }
    if (nextStatement instanceof PsiSwitchLabelStatement) {
      return ((PsiSwitchLabelStatement)nextStatement).getEnclosingSwitchBlock() == switchStatement &&
             !ControlFlowUtils.statementMayCompleteNormally(statement);
    }
    if (nextStatement instanceof PsiBreakStatement) {
      return ((PsiBreakStatement)nextStatement).findExitedStatement() == switchStatement;
    }
    return false;
  }

  public static @Nullable BreakConverter from(PsiSwitchBlock switchStatement) {
    String replacement = switchStatement instanceof PsiSwitchStatement ? getReplacement((PsiStatement)switchStatement) : null;
    if (replacement == null) {
      class Visitor extends JavaRecursiveElementWalkingVisitor {
        boolean hasNonRemovableBreak;

        @Override
        public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
          super.visitBreakStatement(statement);
          if (statement.findExitedStatement() == switchStatement && !isRemovable(switchStatement, statement)) {
            hasNonRemovableBreak = true;
            stopWalking();
          }
        }

        @Override
        public void visitExpression(@NotNull PsiExpression expression) {
          // Going down into any expression seems redundant
        }

        @Override
        public void visitClass(@NotNull PsiClass aClass) {}
      }
      Visitor visitor = new Visitor();
      switchStatement.accept(visitor);
      if (visitor.hasNonRemovableBreak) return null;
    }
    return new BreakConverter(switchStatement, replacement);
  }
}
