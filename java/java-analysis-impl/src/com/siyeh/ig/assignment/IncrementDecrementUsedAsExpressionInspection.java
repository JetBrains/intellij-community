/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.assignment;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CodeBlockSurrounder;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IncrementDecrementUsedAsExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public @NotNull String getID() {
    return "ValueOfIncrementOrDecrementUsed";
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiPostfixExpression postfixExpression) {
      final IElementType tokenType = postfixExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
        return InspectionGadgetsBundle.message(
          "value.of.post.increment.problem.descriptor");
      }
      else {
        return InspectionGadgetsBundle.message(
          "value.of.post.decrement.problem.descriptor");
      }
    }
    else {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)info;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS)) {
        return InspectionGadgetsBundle.message(
          "value.of.pre.increment.problem.descriptor");
      }
      else {
        return InspectionGadgetsBundle.message(
          "value.of.pre.decrement.problem.descriptor");
      }
    }
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    if (PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class, true, PsiMember.class) == null) {
      return null;
    }
    return new IncrementDecrementUsedAsExpressionFix(expression.getText());
  }

  private static class IncrementDecrementUsedAsExpressionFix
    extends PsiUpdateModCommandQuickFix {

    private final String elementText;

    IncrementDecrementUsedAsExpressionFix(String elementText) {
      this.elementText = elementText;
    }

    @Override
    public @NotNull String getName() {
      return InspectionGadgetsBundle.message(
        "increment.decrement.used.as.expression.quickfix",
        elementText);
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("increment.decrement.used.as.expression.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      extractPrefixPostfixExpressionToSeparateStatement(startElement);
    }
  }

  public static PsiExpression getSurroundPrefixPostfixExpression(@NotNull PsiUnaryExpression element) {
    final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    if (statement == null) return null;
    if (statement instanceof PsiLoopStatement) {
      return element;
    }
    final CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(element);
    if (surrounder == null) return null;
    final CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    return result.getExpression();
  }

  public static void extractPrefixPostfixExpressionToSeparateStatement(PsiElement element) {
    final PsiExpression operand;
    if (element instanceof PsiUnaryExpression) {
      operand = ((PsiUnaryExpression)element).getOperand();
      element = getSurroundPrefixPostfixExpression((PsiUnaryExpression)element);
    }
    else {
      assert false;
      return;
    }
    if (operand == null) {
      return;
    }
    final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    if (statement == null) {
      return;
    }
    final PsiElement parent = statement.getParent();
    if (parent == null) {
      return;
    }
    final Project project = element.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final String newStatementText = element.getText() + ';';
    final String operandText = operand.getText();
    final PsiStatement newStatement = factory.createStatementFromText(newStatementText, element);
    if (statement instanceof PsiReturnStatement || statement instanceof PsiYieldStatement || statement instanceof PsiThrowStatement) {
      if (element instanceof PsiPostfixExpression) {
        // special handling of postfix expression in return/yield/throw statement
        final PsiExpression expression;
        if (statement instanceof PsiReturnStatement) {
          expression = ((PsiReturnStatement)statement).getReturnValue();
        } else if (statement instanceof PsiYieldStatement) {
          expression = ((PsiYieldStatement)statement).getExpression();
        } else {
          expression = ((PsiThrowStatement)statement).getException();
        }
        if (expression == null) {
          return;
        }
        final PsiType type = expression.getType();
        if (type == null) {
          return;
        }
        final String[] names = (statement instanceof PsiThrowStatement) ? new String[]{"e", "ex", "exc"} : new String[]{"result"};
        VariableNameGenerator generator = new VariableNameGenerator(expression, VariableKind.LOCAL_VARIABLE);
        if (statement instanceof PsiReturnStatement || statement instanceof PsiYieldStatement) {
          generator = generator.byType(type).byExpression(expression);
        }
        final String variableName = generator.byName(names).generate(true);
        final String newReturnValueText = PsiReplacementUtil.getElementText(expression, element, operandText);
        final String declarationStatementText = type.getCanonicalText() + ' ' + variableName + '=' + newReturnValueText + ';';
        final PsiStatement declarationStatement = factory.createStatementFromText(declarationStatementText, statement);
        parent.addBefore(declarationStatement, statement);
        parent.addBefore(newStatement, statement);
        final String keyword;
        if (statement instanceof PsiReturnStatement) {
          keyword = JavaKeywords.RETURN;
        } else if (statement instanceof PsiYieldStatement) {
          keyword = JavaKeywords.YIELD;
        } else {
          keyword = JavaKeywords.THROW;
        }
        final PsiStatement newReturnStatement = factory.createStatementFromText(keyword + " " + variableName + ';', statement);
        statement.replace(newReturnStatement);
        return;
      }
      else {
        parent.addBefore(newStatement, statement);
      }
    }
    else if (!(statement instanceof PsiForStatement)) {
      if (element instanceof PsiPostfixExpression) {
        parent.addAfter(newStatement, statement);
      }
      else {
        parent.addBefore(newStatement, statement);
      }
    }
    else if (operand instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement target = referenceExpression.resolve();
      if (target != null) {
        final SearchScope useScope = target.getUseScope();
        if (!new LocalSearchScope(statement).equals(useScope)) {
          if (element instanceof PsiPostfixExpression) {
            parent.addAfter(newStatement, statement);
          }
          else {
            parent.addBefore(newStatement, statement);
          }
        }
      }
    }
    if (statement instanceof PsiLoopStatement loopStatement) {
      // in/decrement inside loop statement condition
      final PsiStatement body = loopStatement.getBody();
      if (body instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        if (element instanceof PsiPostfixExpression) {
          final PsiElement firstElement = codeBlock.getFirstBodyElement();
          codeBlock.addBefore(newStatement, firstElement);
        }
        else {
          codeBlock.add(newStatement);
        }
      }
      else {
        final StringBuilder blockText = new StringBuilder();
        blockText.append('{');
        if (element instanceof PsiPostfixExpression) {
          blockText.append(newStatementText);
          if (body != null) {
            blockText.append(body.getText());
          }
        }
        else {
          if (body != null) {
            blockText.append(body.getText());
          }
          blockText.append(newStatementText);
        }
        blockText.append('}');
        final PsiStatement blockStatement = factory.createStatementFromText(blockText.toString(), statement);
        if (body == null) {
          loopStatement.add(blockStatement);
        }
        else {
          body.replace(blockStatement);
        }
      }
    }
    PsiReplacementUtil.replaceExpression((PsiExpression)element, operandText);
  }

  public static boolean isSuitableForReplacement(@NotNull PsiUnaryExpression expression) {
    if (ExpressionUtils.isVoidContext(expression)) {
      return false;
    }
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
      return false;
    }
    final PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
    final PsiForStatement forStatement = PsiTreeUtil.getParentOfType(expression, PsiForStatement.class);
    if (forStatement != null && PsiTreeUtil.isAncestor(forStatement.getInitialization(), expression, false)) {
      return false;
    }
    return statement != null && (CodeBlockSurrounder.canSurround(expression) || statement instanceof PsiLoopStatement);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IncrementDecrementUsedAsExpressionVisitor();
  }

  private static class IncrementDecrementUsedAsExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);

      if (isSuitableForReplacement(expression)) {
        registerError(expression, expression);
      }
    }
  }
}