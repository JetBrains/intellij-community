// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.optionalToIf;

import com.intellij.codeInspection.streamToLoop.ChainContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;


class OptionalToIfContext extends ChainContext {

  private static final Logger LOG = Logger.getInstance(OptionalToIfContext.class);

  private final ChainExpressionModel myModel;

  private String myInitializer;
  private String myElseBranch;

  OptionalToIfContext(@NotNull PsiExpression chainExpression, @NotNull ChainExpressionModel model) {
    super(chainExpression);
    myModel = model;
  }

  @NotNull
  String addInitializer(@NotNull String code) {
    code = drainBeforeSteps() + code + drainAfterSteps();
    if (myInitializer == null) return code;
    return myModel.addInitializer(myModel.createInitializer(myInitializer), code);
  }

  @NotNull
  String createResult(@NotNull String expression) {
    return myInitializer == null ? myModel.createInitializer(expression) : myModel.createResult(expression);
  }

  void setInitializer(@NotNull String initializer) {
    LOG.assertTrue(myInitializer == null);
    myInitializer = initializer;
  }

  void setElseBranch(String elseBranch) {
    myElseBranch = elseBranch;
  }

  @NotNull
  String generateNotNullCondition(@NotNull String arg, @NotNull String code) {
    if (myElseBranch == null) {
      return "if (" + arg + " != null) {\n" +
             code +
             "\n}";
    }
    return "if(" + arg + " == null) " + myElseBranch +
           code;
  }

  @Nullable
  String generateCondition(@NotNull PsiExpression conditional, @NotNull String code) {
    if (myElseBranch == null) {
      return "if (" + conditional.getText() + ") {\n" +
             code +
             "\n}";
    }
    String negated = getNegatedConditional(conditional);
    if (negated == null) return null;
    return "if (" + negated + ")" + myElseBranch +
           code;
  }

  @Nullable
  private static String getNegatedConditional(@NotNull PsiExpression conditional) {
    PsiExpression negated = BoolUtils.getNegated(conditional);
    if (negated != null) return negated.getText();
    PsiBinaryExpression condition = tryCast(conditional, PsiBinaryExpression.class);
    if (condition == null || !ComparisonUtils.isComparison(condition)) return null;
    PsiExpression lhs = condition.getLOperand();
    PsiExpression rhs = condition.getROperand();
    if (rhs == null) return null;
    String negatedToken = ComparisonUtils.getNegatedComparison(condition.getOperationTokenType());
    if (negatedToken == null) return null;
    return lhs.getText() + negatedToken + rhs.getText();
  }

  @Nullable
  static OptionalToIfContext create(@NotNull PsiExpression chainExpression) {
    PsiStatement chainStatement = PsiTreeUtil.getParentOfType(chainExpression, PsiStatement.class);
    if (chainStatement == null) return null;
    ChainExpressionModel model = ChainExpressionModel.create(chainStatement, chainExpression);
    if (model == null) return null;
    return new OptionalToIfContext(chainExpression, model);
  }

  private static abstract class ChainExpressionModel {

    @NotNull
    abstract String createResult(@NotNull String expression);

    @NotNull
    abstract String createInitializer(@NotNull String expression);

    @NotNull
    abstract String addInitializer(@NotNull String initializer, @NotNull String code);

    @Nullable
    static ChainExpressionModel create(@NotNull PsiStatement chainStatement, @NotNull PsiExpression chainExpression) {
      PsiReturnStatement returnStatement = tryCast(chainStatement, PsiReturnStatement.class);
      if (returnStatement != null) return ChainReturn.create(returnStatement, chainExpression);
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(chainStatement);
      if (assignment != null) return ChainAssignment.create(assignment, chainExpression);
      PsiDeclarationStatement declaration = tryCast(chainStatement, PsiDeclarationStatement.class);
      if (declaration != null) return ChainDeclaration.create(declaration, chainExpression);
      PsiExpressionStatement expressionStatement = tryCast(chainStatement, PsiExpressionStatement.class);
      if (expressionStatement != null && PsiUtil.skipParenthesizedExprDown(expressionStatement.getExpression()) == chainExpression) {
        return new ChainStatement();
      }
      return null;
    }
  }

  private static class ChainStatement extends ChainExpressionModel {

    @NotNull
    @Override
    String createResult(@NotNull String expression) {
      return "";
    }

    @NotNull
    @Override
    String createInitializer(@NotNull String expression) {
      return "";
    }

    @NotNull
    @Override
    String addInitializer(@NotNull String initializer, @NotNull String code) {
      return code;
    }
  }

  private static class ChainReturn extends ChainExpressionModel {

    private final PsiReturnStatement myChainReturnCopy;
    private PsiExpression myChainExpressionCopy;

    private ChainReturn(@NotNull PsiReturnStatement chainReturnCopy, @NotNull PsiExpression chainExpressionCopy) {
      myChainReturnCopy = chainReturnCopy;
      myChainExpressionCopy = chainExpressionCopy;
    }

    @NotNull
    @Override
    String createResult(@NotNull String expression) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myChainExpressionCopy.getProject()).getElementFactory();
      PsiExpression newExpression = factory.createExpressionFromText(expression, myChainExpressionCopy);
      myChainExpressionCopy = (PsiExpression)myChainExpressionCopy.replace(newExpression);
      return myChainReturnCopy.getText();
    }

    @NotNull
    @Override
    String createInitializer(@NotNull String expression) {
      return createResult(expression);
    }

    @NotNull
    @Override
    String addInitializer(@NotNull String initializer, @NotNull String code) {
      return code + initializer;
    }

    @Nullable
    private static ChainReturn create(@NotNull PsiReturnStatement chainReturn, @NotNull PsiExpression chainExpression) {
      if (PsiUtil.skipParenthesizedExprDown(chainReturn.getReturnValue()) != chainExpression) return null;
      Object mark = new Object();
      PsiTreeUtil.mark(chainExpression, mark);
      PsiReturnStatement chainReturnCopy = tryCast(chainReturn.copy(), PsiReturnStatement.class);
      if (chainReturnCopy == null) return null;
      PsiExpression chainExpressionCopy = tryCast(PsiTreeUtil.releaseMark(chainReturnCopy, mark), PsiExpression.class);
      if (chainExpressionCopy == null) return null;
      return new ChainReturn(chainReturnCopy, chainExpressionCopy);
    }
  }

  private static class ChainAssignment extends ChainExpressionModel {

    private final String myName;

    private ChainAssignment(@NotNull String name) {
      myName = name;
    }

    @NotNull
    @Override
    String createResult(@NotNull String expression) {
      return myName + " = " + expression + ";";
    }

    @NotNull
    @Override
    String createInitializer(@NotNull String expression) {
      return createResult(expression);
    }

    @NotNull
    @Override
    String addInitializer(@NotNull String initializer, @NotNull String code) {
      return initializer + code;
    }

    @Nullable
    static ChainExpressionModel create(@NotNull PsiAssignmentExpression assignment, @NotNull PsiExpression chainExpression) {
      if (PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()) != chainExpression) return null;
      PsiReferenceExpression ref = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
      if (ref == null) return null;
      PsiVariable variable = tryCast(ref.resolve(), PsiVariable.class);
      if (variable == null || variable.hasModifierProperty(PsiModifier.FINAL)) return null;
      String name = variable.getName();
      return name == null ? null : new ChainAssignment(name);
    }
  }

  private static class ChainDeclaration extends ChainExpressionModel {

    private final String myName;
    private final PsiVariable myVariable;

    ChainDeclaration(@NotNull String name, @NotNull PsiVariable variable) {
      myName = name;
      myVariable = variable;
    }

    @NotNull
    @Override
    String createResult(@NotNull String expression) {
      return myName + " = " + expression + ";";
    }

    @NotNull
    @Override
    String createInitializer(@NotNull String expression) {
      return String.format("%s %s = %s;", myVariable.getType().getCanonicalText(), myName, expression);
    }

    @NotNull
    @Override
    String addInitializer(@NotNull String initializer, @NotNull String code) {
      return initializer + code;
    }

    @Nullable
    static ChainDeclaration create(@NotNull PsiDeclarationStatement declaration, @NotNull PsiExpression chainExpression) {
      PsiElement[] elements = declaration.getDeclaredElements();
      if (elements.length != 1) return null;
      PsiVariable variable = tryCast(elements[0], PsiVariable.class);
      if (variable == null || PsiUtil.skipParenthesizedExprDown(variable.getInitializer()) != chainExpression) return null;
      String name = variable.getName();
      return name == null ? null : new ChainDeclaration(name, variable);
    }
  }
}

