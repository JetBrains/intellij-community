// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.performance;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

public final class ManualArrayToCollectionCopyInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "manual.array.to.collection.copy.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new ManualArrayToCollectionCopyFix();
  }

  static PsiArrayAccessExpression getArrayAccessExpression(PsiForStatement forStatement) {
    final PsiStatement body = getBody(forStatement);
    if (body == null) {
      return null;
    }
    final PsiExpression arrayAccessExpression;
    if (body instanceof PsiExpressionStatement expressionStatement) {
      final PsiExpression expression = expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
        return null;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      PsiExpression[] expressions = argumentList.getExpressions();
      arrayAccessExpression = expressions.length == 0 ? null : expressions[0];
    }
    else if (body instanceof PsiDeclarationStatement declarationStatement) {
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      if (declaredElements.length != 1) {
        return null;
      }
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiVariable variable)) {
        return null;
      }
      arrayAccessExpression = variable.getInitializer();
    }
    else {
      return null;
    }
    final PsiExpression deparenthesizedArgument = PsiUtil.skipParenthesizedExprDown(arrayAccessExpression);
    if (!(deparenthesizedArgument instanceof PsiArrayAccessExpression)) {
      return null;
    }
    return (PsiArrayAccessExpression)deparenthesizedArgument;
  }

  @Nullable
  private static PsiStatement getBody(PsiLoopStatement forStatement) {
    PsiStatement body = forStatement.getBody();
    while (body instanceof PsiBlockStatement blockStatement) {
      final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      final PsiStatement[] statements = codeBlock.getStatements();
      body = statements.length == 0 ? null : statements[0];
    }
    return body;
  }

  private static class ManualArrayToCollectionCopyFix
    extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Collections.addAll(...,...)");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiLoopStatement loop = tryCast(startElement.getParent(), PsiLoopStatement.class);
      String newExpression = null;
      if (loop instanceof PsiForStatement) {
        newExpression = getCollectionsAddAllText((PsiForStatement)loop);
      }
      else if (loop instanceof PsiForeachStatement) {
        newExpression = getCollectionsAddAllText((PsiForeachStatement)loop);
      }
      if (newExpression == null) return;
      PsiReplacementUtil.replaceStatementAndShortenClassNames(loop, newExpression);
    }

    private static @Nullable @NonNls String getCollectionsAddAllText(PsiForeachStatement foreachStatement) {
      final PsiStatement body = getBody(foreachStatement);
      if (!(body instanceof PsiExpressionStatement expressionStatement)) return null;
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expressionStatement.getExpression();
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression collection = ExpressionUtils.getEffectiveQualifier(methodExpression);
      if (collection == null) return null;
      final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
      if (iteratedValue == null) return null;
      final String arrayText = iteratedValue.getText();
      if (PsiUtil.isLanguageLevel5OrHigher(foreachStatement)) {
        return "java.util.Collections.addAll(" + collection.getText() + ',' + arrayText + ");";
      }
      final String collectionText = ParenthesesUtils.getText(collection, PsiPrecedenceUtil.POSTFIX_PRECEDENCE);
      return collectionText + ".addAll(java.util.Arrays.asList(" + arrayText + "));";
    }

    private static @Nullable @NonNls String getCollectionsAddAllText(PsiForStatement forStatement) {
      final PsiExpression expression = forStatement.getCondition();
      final PsiBinaryExpression condition = tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiBinaryExpression.class);
      if (condition == null) return null;
      final PsiDeclarationStatement declaration = tryCast(forStatement.getInitialization(), PsiDeclarationStatement.class);
      if (declaration == null) return null;
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) return null;
      final PsiLocalVariable variable = tryCast(declaredElements[0], PsiLocalVariable.class);
      if (variable == null) return null;
      final String collectionText = buildCollectionText(forStatement);
      final PsiArrayAccessExpression arrayAccessExpression = getArrayAccessExpression(forStatement);
      if (arrayAccessExpression == null) return null;

      final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
      final String arrayText = arrayExpression.getText();
      final PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
      final String indexOffset = getIndexOffset(indexExpression, variable);
      if (indexOffset == null) return null;
      final String fromOffsetText = addIndexOffset(variable.getInitializer(), indexOffset, false);
      if (fromOffsetText == null) return null;
      final IElementType tokenType = condition.getOperationTokenType();
      final PsiExpression limit =
        tokenType == JavaTokenType.LT || tokenType == JavaTokenType.LE ? condition.getROperand() : condition.getLOperand();

      final String toOffsetText = addIndexOffset(limit, indexOffset, tokenType == JavaTokenType.LE || tokenType == JavaTokenType.GE);
      if (toOffsetText == null) return null;

      if (fromOffsetText.equals("0") && toOffsetText.equals(arrayText + ".length") && PsiUtil.isLanguageLevel5OrHigher(forStatement)) {
        return "java.util.Collections.addAll(" + collectionText + ',' + arrayText + ");";
      }
      else {
        @NonNls final StringBuilder buffer = new StringBuilder();
        buffer.append(collectionText);
        buffer.append('.');
        buffer.append("addAll(java.util.Arrays.asList(");
        buffer.append(arrayText);
        buffer.append(')');
        if (!fromOffsetText.equals("0") || !toOffsetText.equals(arrayText + ".length")) {
          buffer.append(".subList(");
          buffer.append(fromOffsetText);
          buffer.append(", ");
          buffer.append(toOffsetText);
          buffer.append(')');
        }
        buffer.append(");");
        return buffer.toString();
      }
    }

    public static String buildCollectionText(PsiForStatement forStatement) {
      PsiStatement body = forStatement.getBody();
      while (body instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 2) {
          body = statements[1];
        }
        else if (statements.length == 1) {
          body = statements[0];
        }
        else {
          return null;
        }
      }
      if (!(body instanceof PsiExpressionStatement)) return null;
      final PsiExpression expression = PsiUtil.skipParenthesizedExprDown(((PsiExpressionStatement)body).getExpression());
      if (!(expression instanceof PsiMethodCallExpression call)) return null;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      return qualifier != null ? qualifier.getText() : null;
    }

    @Nullable
    private static String getIndexOffset(PsiExpression expression, PsiLocalVariable variable) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression == null) {
        return null;
      }
      if (ExpressionUtils.isZero(expression)) {
        return "0";
      }
      final String expressionText = expression.getText();
      final String variableName = variable.getName();
      if (expressionText.equals(variableName)) {
        return "0";
      }
      if (expression instanceof PsiBinaryExpression binaryExpression) {
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();

        final String rhsText = getIndexOffset(rhs, variable);
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (ExpressionUtils.isZero(lhs)) {
          if (tokenType.equals(JavaTokenType.MINUS)) {
            return '-' + rhsText;
          }
          return rhsText;
        }
        final String lhsText = getIndexOffset(lhs, variable);
        if (ExpressionUtils.isZero(rhs)) {
          return lhsText;
        }
        return collapseConstant(lhsText + " " + sign.getText() + " " + rhsText, variable);
      }
      return collapseConstant(expressionText, variable);
    }

    private static String addIndexOffset(PsiExpression expression, String indexOffset, boolean plusOne) {
      if (expression == null) {
        return null;
      }
      if (plusOne) {
        indexOffset = collapseConstant("(" + indexOffset + ") + 1", expression);
      }
      final String expressionText = expression.getText();
      if ("0".equals(indexOffset)) {
        return expressionText;
      }

      if (expression instanceof PsiBinaryExpression binaryExpression) {
        final IElementType tokenType =
          binaryExpression.getOperationTokenType();
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();

        if (tokenType == JavaTokenType.PLUS) {
          Object rhConstant = ExpressionUtils.computeConstantExpression(rhs);
          if (rhConstant != null) {
            final String rhText = collapseConstant(
              rhConstant + " + (" + indexOffset + ")", expression);
            if ("0".equals(rhText)) {
              return lhs.getText();
            }
            return lhs.getText() + getAddendum(rhText, expression);
          }
        }

        if (tokenType == JavaTokenType.MINUS) {
          Object rhConstant = ExpressionUtils.computeConstantExpression(rhs);
          if (rhConstant != null) {
            final String rhText = collapseConstant(
              "(" + indexOffset + ") - " + rhConstant, expression);
            if ("0".equals(rhText)) {
              return lhs.getText();
            }
            return lhs.getText() + getAddendum(rhText, expression);
          }
        }

        if (rhs != null &&
            (tokenType == JavaTokenType.PLUS ||
             tokenType == JavaTokenType.MINUS)) {
          Object lhConstant = ExpressionUtils.computeConstantExpression(lhs);
          if (lhConstant != null) {
            String lhText = collapseConstant(
              lhConstant + " + (" + indexOffset + ")", expression);
            if ("0".equals(lhText)) {
              return tokenType == JavaTokenType.MINUS ? "-" + rhs.getText() : rhs.getText();
            }
            return lhText + (tokenType == JavaTokenType.MINUS ? " - " : " + ") + rhs.getText();
          }
        }
      }

      final String addendum = getAddendum(indexOffset, expression);
      final int precedence = ParenthesesUtils.getPrecedence(expression);
      final String text = precedence > ParenthesesUtils.ADDITIVE_PRECEDENCE
                          ? '(' + expressionText + ")" + addendum
                          : expressionText + addendum;
      return collapseConstant(text, expression);
    }

    private static String getAddendum(String expressionText, PsiElement context) {
      if (expressionText.startsWith("-")) {
        final String negatedExpressionText = expressionText.substring(1);
        final Object lhConstant = computeConstant(negatedExpressionText, context);
        if (lhConstant != null) {
          return " - " + lhConstant.toString();
        }
        return " + (" + expressionText + ")";
      }
      return " + " + expressionText;
    }

    private static String collapseConstant(String expressionText, PsiElement context) {
      final Object fromOffsetConstant = computeConstant(expressionText, context);
      return fromOffsetConstant != null ? fromOffsetConstant.toString() : expressionText;
    }

    private static Object computeConstant(String expressionText,
                                          PsiElement context) {
      final Project project = context.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiExpression fromOffsetExpression =
        factory.createExpressionFromText(expressionText, context);
      return ExpressionUtils.computeConstantExpression(fromOffsetExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ManualArrayToCollectionCopyVisitor();
  }

  private static class ManualArrayToCollectionCopyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      final PsiStatement initialization = statement.getInitialization();
      if (!(initialization instanceof PsiDeclarationStatement declaration)) return;
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) return;
      final PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiLocalVariable variable)) return;
      final PsiExpression initialValue = variable.getInitializer();
      if (initialValue == null) return;
      final PsiExpression condition = statement.getCondition();
      if (!ExpressionUtils.isVariableLessThanComparison(condition, variable)) return;
      final PsiStatement update = statement.getUpdate();
      if (!VariableAccessUtils.variableIsIncremented(variable, update)) return;
      final PsiArrayAccessExpression arrayAccessExpression = getArrayAccessExpression(statement);
      if (arrayAccessExpression == null) return;
      final PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
      final PsiType type = arrayExpression.getType();
      if (!(type instanceof PsiArrayType) || type.getDeepComponentType() instanceof PsiPrimitiveType) return;
      final PsiStatement body = statement.getBody();
      if (!bodyIsArrayToCollectionCopy(body, variable, true)) return;
      registerStatementError(statement);
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) return;
      final PsiType type = iteratedValue.getType();
      if (!(type instanceof PsiArrayType arrayType)) return;
      final PsiType componentType = arrayType.getComponentType();
      if (componentType instanceof PsiPrimitiveType) return;
      final PsiParameter parameter = statement.getIterationParameter();
      final PsiStatement body = statement.getBody();
      if (!bodyIsArrayToCollectionCopy(body, parameter, false)) return;
      registerStatementError(statement);
    }

    private static boolean bodyIsArrayToCollectionCopy(PsiStatement body, PsiVariable variable, boolean shouldBeOffsetArrayAccess) {
      if (body instanceof PsiExpressionStatement expressionStatement) {
        final PsiExpression expression = expressionStatement.getExpression();
        return expressionIsArrayToCollectionCopy(expression, variable, shouldBeOffsetArrayAccess);
      }
      else if (body instanceof PsiBlockStatement blockStatement) {
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1) {
          return bodyIsArrayToCollectionCopy(statements[0], variable, shouldBeOffsetArrayAccess);
        }
        else if (statements.length == 2) {
          final PsiStatement statement = statements[0];
          if (!(statement instanceof PsiDeclarationStatement declarationStatement)) return false;
          final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
          if (declaredElements.length != 1) return false;
          final PsiElement declaredElement = declaredElements[0];
          if (!(declaredElement instanceof PsiVariable localVariable)) return false;
          final PsiExpression initializer = localVariable.getInitializer();
          if (!ExpressionUtils.isOffsetArrayAccess(initializer, variable)) return false;
          return bodyIsArrayToCollectionCopy(statements[1], localVariable, false);
        }
      }
      return false;
    }

    private static boolean expressionIsArrayToCollectionCopy(PsiExpression expression,
                                                             PsiVariable variable,
                                                             boolean shouldBeOffsetArrayAccess) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression == null) return false;
      if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) return false;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) return false;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression) &&
          !(qualifier instanceof PsiThisExpression) &&
          !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      if (VariableAccessUtils.variableIsUsed(variable, qualifier)) return false;
      final PsiExpression argument = arguments[0];
      final PsiType argumentType = argument.getType();
      if (argumentType instanceof PsiPrimitiveType) return false;
      if (SideEffectChecker.mayHaveSideEffects(argument)) return false;
      if (shouldBeOffsetArrayAccess) {
        if (!ExpressionUtils.isOffsetArrayAccess(argument, variable)) return false;
      }
      else {
        if (!ExpressionUtils.isReferenceTo(argument, variable)) return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) return false;
      @NonNls final String name = method.getName();
      if (!name.equals("add")) return false;
      final PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION);
    }
  }
}