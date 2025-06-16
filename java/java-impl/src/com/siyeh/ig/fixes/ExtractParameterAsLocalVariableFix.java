// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExtractParameterAsLocalVariableFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiExpression expression)) return;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiReferenceExpression parameterReference)) return;
    if (!(parameterReference.resolve() instanceof PsiParameter parameter)) return;
    final PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiLambdaExpression lambda) {
      CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock(lambda);
    }
    else if (declarationScope instanceof PsiForeachStatement foreachStatement) {
      final PsiStatement body = foreachStatement.getBody();
      if (body == null) return;
      BlockUtils.expandSingleStatementToBlockStatement(body);
    }
    final PsiElement body = BlockUtils.getBody(declarationScope);
    if (body == null) return;
    assert body instanceof PsiCodeBlock;
    final String parameterName = parameter.getName();
    final PsiExpression rhs = parameterReference.isValid() ? getRightSideIfLeftSideOfSimpleAssignment(parameterReference, body) : null;
    PsiType type = parameter.getType();
    if (type instanceof PsiEllipsisType ellipsisType) {
      type = ellipsisType.toArrayType();
    }
    if (type instanceof PsiLambdaParameterType) return;
    List<String> names = new VariableNameGenerator(body, VariableKind.LOCAL_VARIABLE)
      .byExpression(rhs).byName(parameterName).byType(type).generateAll(true);
    final String variableName = names.get(0);
    final CommentTracker tracker = new CommentTracker();
    final String initializerText = (rhs == null) ? parameterName : tracker.text(rhs);
    PsiDeclarationStatement newStatement = (PsiDeclarationStatement)
      JavaPsiFacade.getElementFactory(project).createStatementFromText(
        type.getCanonicalText() + ' ' + variableName + '=' + initializerText + ';', body);
    final PsiCodeBlock codeBlock = (PsiCodeBlock)body;
    List<PsiReferenceExpression> refs = new ArrayList<>();
    PsiStatement anchor = null;
    for (PsiStatement statement : codeBlock.getStatements()) {
      if (anchor == null) {
        if (rhs == null && !JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
          anchor = statement;
          SyntaxTraverser.psiTraverser(statement).filter(PsiReferenceExpression.class)
            .filter(ref -> ref.isReferenceTo(parameter)).addAllTo(refs);
        }
        else if (statement.getTextRange().contains(parameterReference.getTextRange())) {
          anchor = statement;
        }
      }
      else {
        SyntaxTraverser.psiTraverser(statement).filter(PsiReferenceExpression.class)
          .filter(ref -> ref.isReferenceTo(parameter)).addAllTo(refs);
      }
    }
    assert anchor != null;
    newStatement = (PsiDeclarationStatement)(rhs == null
                                             ? codeBlock.addBefore(newStatement, anchor)
                                             : tracker.replaceAndRestoreComments(anchor, newStatement));
    replaceReferences(refs, variableName, body);
    final PsiLocalVariable variable = (PsiLocalVariable)newStatement.getDeclaredElements()[0];
    updater.rename(variable, names);
  }

  private static void replaceReferences(Collection<PsiReferenceExpression> collection, String newVariableName, PsiElement context) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    final PsiReferenceExpression newReference = (PsiReferenceExpression)factory.createExpressionFromText(newVariableName, context);
    for (PsiReferenceExpression reference : collection) {
      reference.replace(newReference);
    }
  }

  private static PsiExpression getRightSideIfLeftSideOfSimpleAssignment(PsiReferenceExpression reference, PsiElement block) {
    if (reference == null) return null;
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(reference);
    if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) return null;
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    if (!JavaTokenType.EQ.equals(tokenType)) return null;
    final PsiExpression lExpression = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
    if (!reference.equals(lExpression)) return null;
    final PsiExpression rExpression = assignmentExpression.getRExpression();
    if (PsiUtil.skipParenthesizedExprDown(rExpression) instanceof PsiAssignmentExpression) return null;
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiExpressionStatement) || grandParent.getParent() != block) return null;
    return rExpression;
  }
}