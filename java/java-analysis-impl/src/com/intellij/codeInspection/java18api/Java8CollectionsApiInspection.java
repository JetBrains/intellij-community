/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class Java8CollectionsApiInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(Java8CollectionsApiInspection.class);

  public boolean myReportContainsCondition;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Report when \'containsKey\' is used in condition (may change semantics)", this,
                                          "myReportContainsCondition");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        final ConditionInfo conditionInfo = extractConditionInfo(expression.getCondition());
        if (conditionInfo == null) return;
        final PsiExpression thenExpression = expression.getThenExpression();
        final PsiExpression elseExpression = expression.getElseExpression();
        if (thenExpression == null || elseExpression == null) return;
        analyzeCorrespondenceOfPutAndGet(conditionInfo.isInverted() ? thenExpression : elseExpression,
                                         conditionInfo.isInverted() ? elseExpression : thenExpression,
                                         conditionInfo.getQualifier(), conditionInfo.getContainsKey(),
                                         holder, expression);
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        final PsiExpression condition = statement.getCondition();
        final ConditionInfo conditionInfo = extractConditionInfo(condition);
        if (conditionInfo == null) return;
        PsiStatement maybeGetBranch = conditionInfo.isInverted() ? statement.getElseBranch() : statement.getThenBranch();
        if (maybeGetBranch instanceof PsiBlockStatement) {
          final PsiStatement[] getBranchStatements = ((PsiBlockStatement)maybeGetBranch).getCodeBlock().getStatements();
          if (getBranchStatements.length > 1) {
            return;
          }
          maybeGetBranch = getBranchStatements.length == 0 ? null : getBranchStatements[0];
        }
        final PsiStatement branch = conditionInfo.isInverted() ? statement.getThenBranch() : statement.getElseBranch();
        final PsiStatement maybePutStatement;
        if (branch instanceof PsiBlockStatement) {
          final PsiStatement[] statements = ((PsiBlockStatement)branch).getCodeBlock().getStatements();
          if (statements.length == 0) return;
          if (statements.length != 1) {
            return;
          }
          maybePutStatement = statements[statements.length - 1];
        }
        else {
          maybePutStatement = branch;
        }
        if (maybePutStatement != null) {
          analyzeCorrespondenceOfPutAndGet(maybePutStatement, maybeGetBranch, conditionInfo.getQualifier(), conditionInfo.getContainsKey(),
                                           holder, statement);
        }
      }
    };
  }

  @Nullable
  private ConditionInfo extractConditionInfo(PsiExpression condition) {
    final ConditionInfo info = extractConditionInfoIfGet(condition);
    if (info != null) {
      return info;
    }
    return !myReportContainsCondition ? null : extractConditionInfoIfContains(condition);
  }

  @Nullable
  private static ConditionInfo extractConditionInfoIfGet(PsiExpression condition) {
    if (condition instanceof PsiBinaryExpression &&
        (((PsiBinaryExpression)condition).getOperationSign().getTokenType().equals(JavaTokenType.EQEQ) ||
         ((PsiBinaryExpression)condition).getOperationSign().getTokenType().equals(JavaTokenType.NE))) {
      final List<PsiExpression> operands =
        ContainerUtil.list(((PsiBinaryExpression)condition).getLOperand(), ((PsiBinaryExpression)condition).getROperand());

      PsiExpression getQualifier = null;
      PsiExpression keyExpression = null;
      boolean isNullFound = false;
      for (PsiExpression operand : operands) {
        if (operand == null) return null;
        if (operand instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression maybeGetCall = (PsiMethodCallExpression)operand;
          if (!isJavaUtilMapMethodWithName(maybeGetCall, "get")) {
            return null;
          }
          final PsiExpression[] arguments = maybeGetCall.getArgumentList().getExpressions();
          if (arguments.length != 1) return null;
          getQualifier = maybeGetCall.getMethodExpression().getQualifierExpression();
          keyExpression = arguments[0];
        }
        else if (operand instanceof PsiLiteralExpression && PsiKeyword.NULL.equals(operand.getText())) {
          isNullFound = true;
        }
      }
      if (getQualifier == null && keyExpression == null && isNullFound) {
        return null;
      }

      return new ConditionInfo(getQualifier, keyExpression,
                               ((PsiBinaryExpression)condition).getOperationSign().getTokenType().equals(JavaTokenType.EQEQ));
    }
    return null;
  }

  @Nullable
  private static ConditionInfo extractConditionInfoIfContains(PsiExpression condition) {
    boolean inverted = false;
    final PsiMethodCallExpression conditionMethodCall;
    if (condition instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)condition;
      if (JavaTokenType.EXCL.equals(prefixExpression.getOperationSign().getTokenType()) &&
          prefixExpression.getOperand() instanceof PsiMethodCallExpression) {
        conditionMethodCall = (PsiMethodCallExpression)prefixExpression.getOperand();
        inverted = true;
      }
      else {
        return null;
      }
    }
    else if (condition instanceof PsiMethodCallExpression) {
      conditionMethodCall = (PsiMethodCallExpression)condition;
    }
    else {
      return null;
    }
    if (!isJavaUtilMapMethodWithName(conditionMethodCall, "containsKey")) {
      return null;
    }
    final PsiExpression containsQualifier = conditionMethodCall.getMethodExpression().getQualifierExpression();
    if (containsQualifier == null) {
      return null;
    }
    final PsiExpression[] expressions = conditionMethodCall.getArgumentList().getExpressions();
    if (expressions.length != 1) {
      return null;
    }
    PsiExpression containsKey = expressions[0];
    return new ConditionInfo(containsQualifier, containsKey, inverted);
  }

  private static void analyzeCorrespondenceOfPutAndGet(@NotNull PsiElement adjustedElseBranch,
                                                       @Nullable PsiElement adjustedThenBranch,
                                                       @Nullable PsiExpression containsQualifier,
                                                       @Nullable PsiExpression containsKey,
                                                       @NotNull ProblemsHolder holder,
                                                       @NotNull PsiElement context) {
    final PsiElement maybePutMethodCall;
    final PsiElement maybeGetMethodCall;
    if (adjustedThenBranch == null) {
      maybeGetMethodCall = null;
      if (adjustedElseBranch instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)adjustedElseBranch).getExpression();
        if (expression instanceof PsiMethodCallExpression && isJavaUtilMapMethodWithName((PsiMethodCallExpression)expression, "put")) {
          maybePutMethodCall = expression;
        }
        else {
          maybePutMethodCall = null;
        }
      }
      else {
        maybePutMethodCall = null;
      }
    }
    else {
      if (adjustedElseBranch instanceof PsiStatement && adjustedThenBranch instanceof PsiStatement) {
        final EquivalenceChecker.Decision decision = EquivalenceChecker.statementsAreEquivalentDecision((PsiStatement)adjustedElseBranch,
                                                                                                        (PsiStatement)adjustedThenBranch);
        maybePutMethodCall = decision.getLeftDiff();
        maybeGetMethodCall = decision.getRightDiff();
      }
      else {
        maybePutMethodCall = adjustedElseBranch;
        maybeGetMethodCall = adjustedThenBranch;
      }
    }
    if (maybePutMethodCall instanceof PsiMethodCallExpression &&
        (maybeGetMethodCall == null || maybeGetMethodCall instanceof PsiMethodCallExpression)) {
      final PsiMethodCallExpression putMethodCall = (PsiMethodCallExpression)maybePutMethodCall;
      final PsiMethodCallExpression getMethodCall = (PsiMethodCallExpression)maybeGetMethodCall;
      final PsiExpression putQualifier = putMethodCall.getMethodExpression().getQualifierExpression();
      final PsiExpression getQualifier = getMethodCall == null ? null : getMethodCall.getMethodExpression().getQualifierExpression();
      if ((getMethodCall == null || EquivalenceChecker.expressionsAreEquivalent(putQualifier, getQualifier)) &&
          EquivalenceChecker.expressionsAreEquivalent(putQualifier, containsQualifier) &&
          isJavaUtilMapMethodWithName(putMethodCall, "put") &&
          (getMethodCall == null || isJavaUtilMapMethodWithName(getMethodCall, "get"))) {

        PsiExpression getArgument;
        if (getMethodCall != null) {
          final PsiExpression[] arguments = getMethodCall.getArgumentList().getExpressions();
          if (arguments.length != 1) {
            return;
          }
          getArgument = arguments[0];
        }
        else {
          getArgument = null;
        }

        final PsiExpression[] putArguments = putMethodCall.getArgumentList().getExpressions();
        if (putArguments.length != 2) {
          return;
        }
        PsiExpression putKeyArgument = putArguments[0];

        if (EquivalenceChecker.expressionsAreEquivalent(containsKey, putKeyArgument) &&
            (getArgument == null || EquivalenceChecker.expressionsAreEquivalent(getArgument, putKeyArgument))) {
          holder.registerProblem(context, QuickFixBundle.message("java.8.collections.api.inspection.description"),
                                 new ReplaceWithMapPutIfAbsentFix(putMethodCall));
        }
      }
    }
  }

  private static boolean isJavaUtilMapMethodWithName(@NotNull PsiMethodCallExpression methodCallExpression, @NotNull String expectedName) {
    if (!expectedName.equals(methodCallExpression.getMethodExpression().getReferenceName())) {
      return false;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return false;
    PsiMethod[] superMethods = method.findDeepestSuperMethods();
    if (superMethods.length == 0) {
      superMethods = new PsiMethod[]{method};
    }
    for (PsiMethod psiMethod : superMethods) {
      final PsiClass aClass = psiMethod.getContainingClass();
      if (aClass != null && CommonClassNames.JAVA_UTIL_MAP.equals(aClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  private static class ConditionInfo {
    private final PsiExpression myQualifier;
    private final PsiExpression myContainsKey;
    private final boolean myInverted;

    private ConditionInfo(PsiExpression qualifier, PsiExpression containsKey, boolean inverted) {
      myQualifier = qualifier;
      myContainsKey = containsKey;
      myInverted = inverted;
    }

    public PsiExpression getQualifier() {
      return myQualifier;
    }

    public PsiExpression getContainsKey() {
      return myContainsKey;
    }

    public boolean isInverted() {
      return myInverted;
    }
  }
}