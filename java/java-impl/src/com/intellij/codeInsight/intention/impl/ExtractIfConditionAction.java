/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.JavaFactoryProvider;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public class ExtractIfConditionAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null || ifStatement.getCondition() == null || !(ifStatement.getCondition() instanceof PsiBinaryExpression)) {
      return false;
    }

    final PsiExpression condition = ifStatement.getCondition();

    if (condition == null || !(condition instanceof PsiBinaryExpression)) {
      return false;
    }

    final PsiBinaryExpression binaryCondition = (PsiBinaryExpression)condition;
    final PsiType expressionType = binaryCondition.getType();
    if (expressionType == null || !PsiType.BOOLEAN.isAssignableFrom(expressionType)) {
      return false;
    }

    final IElementType operation = binaryCondition.getOperationTokenType();

    if (operation != JavaTokenType.OROR && operation != JavaTokenType.ANDAND) {
      return false;
    }

    final PsiExpression lOperand = binaryCondition.getLOperand();
    final PsiExpression rOperand = binaryCondition.getROperand();

    if (rOperand == null) {
      return false;
    }

    final TextRange lOperandTextRange = lOperand.getTextRange();
    final TextRange rOperandTextRange = rOperand.getTextRange();
    final TextRange elementTextRange = element.getTextRange();

    if (lOperandTextRange == null || rOperandTextRange == null || elementTextRange == null) {
      return false;
    }

    if (lOperandTextRange.contains(elementTextRange)) {
      setText(CodeInsightBundle.message("intention.extract.if.condition.text", lOperand.getText()));
      return true;
    }

    if (rOperandTextRange.contains(elementTextRange)) {
      setText(CodeInsightBundle.message("intention.extract.if.condition.text", rOperand.getText()));
      return true;
    }

    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null || ifStatement.getCondition() == null || !(ifStatement.getCondition() instanceof PsiBinaryExpression)) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final PsiStatement newIfStatement = create(factory, ifStatement, element);
    if (newIfStatement == null) {
      return;
    }

    ifStatement.replace(codeStyleManager.reformat(newIfStatement));
  }

  @Nullable
  private static PsiStatement create(@NotNull PsiElementFactory factory,
                                     @NotNull PsiIfStatement ifStatement,
                                     @NotNull PsiElement element) {

    final PsiExpression condition = ifStatement.getCondition();

    if (condition == null || !(condition instanceof PsiBinaryExpression)) {
      return null;
    }

    final PsiBinaryExpression binaryCondition = (PsiBinaryExpression)condition;

    final PsiExpression lOperand = binaryCondition.getLOperand();
    final PsiExpression rOperand = binaryCondition.getROperand();

    if (rOperand == null) {
      return null;
    }

    final TextRange lOperandTextRange = lOperand.getTextRange();
    final TextRange rOperandTextRange = rOperand.getTextRange();
    final TextRange elementTextRange = element.getTextRange();

    if (lOperandTextRange == null || rOperandTextRange == null) {
      return null;
    }

    if (lOperandTextRange.contains(elementTextRange)) {
      return create(factory, ifStatement.getThenBranch(), ifStatement.getElseBranch(), lOperand, rOperand, binaryCondition.getOperationTokenType());
    }
    else if (rOperandTextRange.contains(elementTextRange)) {
      return create(factory, ifStatement.getThenBranch(), ifStatement.getElseBranch(), rOperand, lOperand, binaryCondition.getOperationTokenType());
    }

    return null;
  }

  @Nullable
  private static PsiStatement create(@NotNull PsiElementFactory factory,
                                     @Nullable PsiStatement thenBranch,
                                     @Nullable PsiStatement elseBranch,
                                     @NotNull PsiExpression extract,
                                     @NotNull PsiExpression leave,
                                     @NotNull IElementType operation) {
    if (thenBranch == null) {
      return null;
    }

    if (operation == JavaTokenType.OROR) {
      return createOrOr(factory, thenBranch, elseBranch, extract, leave);
    }
    if (operation == JavaTokenType.ANDAND) {
      return createAndAnd(factory, thenBranch, elseBranch, extract, leave);
    }

    return null;
  }

  @NotNull
  private static PsiStatement createAndAnd(@NotNull PsiElementFactory factory,
                                           @NotNull PsiStatement thenBranch,
                                           @Nullable PsiStatement elseBranch,
                                           @NotNull PsiExpression extract,
                                           @NotNull PsiExpression leave) {

    return factory.createStatementFromText(
      createIfString(extract,
                     createIfString(leave, thenBranch, elseBranch),
                     elseBranch
      ),
      thenBranch
    );
  }

  @NotNull
  private static PsiStatement createOrOr(@NotNull PsiElementFactory factory,
                                         @NotNull PsiStatement thenBranch,
                                         @Nullable PsiStatement elseBranch,
                                         @NotNull PsiExpression extract,
                                         @NotNull PsiExpression leave) {

    return factory.createStatementFromText(
      createIfString(extract, thenBranch,
                     createIfString(leave, thenBranch, elseBranch)
      ),
      thenBranch
    );
  }

  @NotNull
  private static String createIfString(@NotNull PsiExpression condition,
                                       @NotNull PsiStatement thenBranch,
                                       @Nullable PsiStatement elseBranch) {
    return createIfString(condition.getText(), toThenBranchString(thenBranch), toElseBranchString(elseBranch));
  }

  @NotNull
  private static String createIfString(@NotNull PsiExpression condition,
                                       @NotNull PsiStatement thenBranch,
                                       @Nullable String elseBranch) {
    return createIfString(condition.getText(), toThenBranchString(thenBranch), elseBranch);
  }

  @NotNull
  private static String createIfString(@NotNull PsiExpression condition,
                                       @NotNull String thenBranch,
                                       @Nullable PsiStatement elseBranch) {
    return createIfString(condition.getText(), thenBranch, toElseBranchString(elseBranch));
  }

  @NotNull
  private static String createIfString(@NotNull String condition,
                                       @NotNull String thenBranch,
                                       @Nullable String elseBranch) {
    final String elsePart = elseBranch != null ? " else " + elseBranch : "";
    return "if (" + condition + ")\n" + thenBranch + elsePart;
  }

  @NotNull
  private static String toThenBranchString(@NotNull PsiStatement statement) {
    if (!(statement instanceof PsiBlockStatement)) {
      return "{ " + statement.getText() + " }";
    }

    return statement.getText();
  }

  @Nullable
  private static String toElseBranchString(@Nullable PsiStatement statement) {
    if (statement == null) {
      return null;
    }

    if (statement instanceof PsiBlockStatement || statement instanceof PsiIfStatement) {
      return statement.getText();
    }

    return "{ " + statement.getText() + " }";
  }

  @Nullable
  private static PsiExpression findOperand(@NotNull PsiElement e, @NotNull PsiPolyadicExpression expression) {
    final TextRange elementTextRange = e.getTextRange();

    for (PsiExpression operand : expression.getOperands()) {
      final TextRange operandTextRange = operand.getTextRange();
      if (operandTextRange != null && operandTextRange.contains(elementTextRange)) {
        return operand;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.extract.if.condition.family");
  }
}
