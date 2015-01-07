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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public class ExtractIfConditionAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null || ifStatement.getCondition() == null) {
      return false;
    }

    final PsiExpression condition = ifStatement.getCondition();

    if (condition == null || !(condition instanceof PsiPolyadicExpression)) {
      return false;
    }

    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
    final PsiType expressionType = polyadicExpression.getType();
    if (expressionType == null || !PsiType.BOOLEAN.isAssignableFrom(expressionType)) {
      return false;
    }

    final IElementType operation = polyadicExpression.getOperationTokenType();

    if (operation != JavaTokenType.OROR && operation != JavaTokenType.ANDAND) {
      return false;
    }

    final PsiExpression operand = findOperand(element, polyadicExpression);

    if (operand == null) {
      return false;
    }
    setText(CodeInsightBundle.message("intention.extract.if.condition.text", PsiExpressionTrimRenderer.render(operand)));
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) {
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

    if (condition == null || !(condition instanceof PsiPolyadicExpression)) {
      return null;
    }

    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;

    final PsiExpression operand = findOperand(element, polyadicExpression);

    if (operand == null) {
      return null;
    }


    return create(
      factory,
      ifStatement.getThenBranch(), ifStatement.getElseBranch(),
      operand,
      removeOperand(factory, polyadicExpression, operand),
      polyadicExpression.getOperationTokenType()
    );
  }

  @NotNull
  private static PsiExpression removeOperand(@NotNull PsiElementFactory factory,
                                             @NotNull PsiPolyadicExpression expression,
                                             @NotNull PsiExpression operand) {
    final StringBuilder sb = new StringBuilder();
    for (PsiExpression e : expression.getOperands()) {
      if (e == operand) continue;
      final PsiJavaToken token = expression.getTokenBeforeOperand(e);
      if (token != null && sb.length() != 0) {
        sb.append(token.getText()).append(" ");
      }
      sb.append(e.getText());
    }
    return factory.createExpressionFromText(sb.toString(), expression);
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
    return createIfString(condition.getText(), toThenBranchString(thenBranch), toElseBranchString(elseBranch, false));
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
    return createIfString(condition.getText(), thenBranch, toElseBranchString(elseBranch, true));
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
  private static String toElseBranchString(@Nullable PsiStatement statement, boolean skipElse) {
    if (statement == null) {
      return null;
    }

    if (statement instanceof PsiBlockStatement || skipElse && statement instanceof PsiIfStatement) {
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
