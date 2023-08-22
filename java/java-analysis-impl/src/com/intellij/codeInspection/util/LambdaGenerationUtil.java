// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.util;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Utility methods which are helpful to generate new lambda expressions in quick-fixes
 *
 * @author Tagir Valeev
 */
public final class LambdaGenerationUtil {
  /**
   * Tests the element (expression or statement) whether it could be converted to the body
   * of lambda expression mapped to functional interface which SAM does not declare any
   * checked exceptions. The following things are checked:
   *
   * <p>1. The expression should not throw checked exceptions</p>
   * <p>2. The expression should not refer any variables which are not effectively final</p>
   * <p>3. No control flow instructions inside which may jump out of the supplied lambdaCandidate</p>
   *
   * @param lambdaCandidate an expression or statement to test
   * @return true if this expression or statement can be converted to lambda
   */
  @Contract("null -> false")
  public static boolean canBeUncheckedLambda(@Nullable PsiElement lambdaCandidate) {
    return canBeUncheckedLambda(lambdaCandidate, var -> false);
  }

  /**
   * Tests the element (expression or statement) whether it could be converted to the body
   * of lambda expression mapped to functional interface which SAM does not declare any
   * checked exceptions. The following things are checked:
   *
   * <p>1. The expression should not throw checked exceptions</p>
   * <p>2. The expression should not refer any variables which are not effectively final
   *       and not allowed by specified predicate</p>
   * <p>3. No control flow instructions inside which may jump out of the supplied lambdaCandidate</p>
   *
   * @param lambdaCandidate an expression or statement to test
   * @param variableAllowedPredicate a predicate which returns true if the variable is allowed to be present inside {@code lambdaCandidate}
   *                even if it's not effectively final (e.g. it will be replaced by something else when moved to lambda)
   * @return true if this expression or statement can be converted to lambda
   */
  @Contract("null, _ -> false")
  public static boolean canBeUncheckedLambda(@Nullable PsiElement lambdaCandidate, @NotNull Predicate<? super PsiVariable> variableAllowedPredicate) {
    if(!(lambdaCandidate instanceof PsiExpression) && !(lambdaCandidate instanceof PsiStatement)) return false;
    if(!ExceptionUtil.getThrownCheckedExceptions(lambdaCandidate).isEmpty()) return false;
    CanBeLambdaBodyVisitor visitor = new CanBeLambdaBodyVisitor(lambdaCandidate, variableAllowedPredicate);
    lambdaCandidate.accept(visitor);
    return visitor.canBeLambdaBody();
  }

  private static class CanBeLambdaBodyVisitor extends JavaRecursiveElementWalkingVisitor {
    // Throws is not handled here: it's usually not a problem to move "throws <UncheckedException>" inside lambda.
    private boolean myCanBeLambdaBody = true;
    private final PsiElement myRoot;
    private final Predicate<? super PsiVariable> myVariableAllowedPredicate;

    CanBeLambdaBodyVisitor(PsiElement root, Predicate<? super PsiVariable> variableAllowedPredicate) {
      myRoot = root;
      myVariableAllowedPredicate = variableAllowedPredicate;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if(!myCanBeLambdaBody) return;
      super.visitElement(element);
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // do not go down the local/anonymous classes
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
      // do not go down the nested lambda expressions
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if(!myCanBeLambdaBody) return;
      super.visitReferenceExpression(expression);
      PsiElement element = expression.resolve();
      if (element instanceof PsiVariable && !isAllowedInLambda(expression, (PsiVariable)element)) {
        myCanBeLambdaBody = false;
      }
    }

    private boolean isAllowedInLambda(PsiReferenceExpression expression, PsiVariable variable) {
      if (myVariableAllowedPredicate.test(variable)) return true;
      if (PsiTreeUtil.isAncestor(myRoot, variable, true)) return true;
      if (variable instanceof PsiField) {
        return !variable.hasModifierProperty(PsiModifier.FINAL) || !PsiUtil.isAccessedForWriting(expression);
      }
      return !PsiUtil.isAccessedForWriting(expression) && HighlightControlFlowUtil.isEffectivelyFinal(variable, myRoot, null);
    }

    @Override
    public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
      PsiStatement exitedStatement = statement.findExitedStatement();
      if(exitedStatement == null || !PsiTreeUtil.isAncestor(myRoot, exitedStatement, false)) {
        myCanBeLambdaBody = false;
      }
      super.visitBreakStatement(statement);
    }

    @Override
    public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
      PsiStatement continuedStatement = statement.findContinuedStatement();
      if(continuedStatement == null || !PsiTreeUtil.isAncestor(myRoot, continuedStatement, false)) {
        myCanBeLambdaBody = false;
      }
      super.visitContinueStatement(statement);
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      myCanBeLambdaBody = false;
    }

    public boolean canBeLambdaBody() {
      return myCanBeLambdaBody;
    }
  }
}
