// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExtractParameterAsLocalVariableFix extends InspectionGadgetsFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("extract.parameter.as.local.variable.quickfix");
  }

  @Override
  public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PsiExpression)) {
      return;
    }
    final PsiExpression expression = PsiUtil.skipParenthesizedExprDown((PsiExpression)element);
    if (!(expression instanceof PsiReferenceExpression parameterReference)) {
      return;
    }
    final PsiElement target = parameterReference.resolve();
    if (!(target instanceof PsiParameter parameter)) {
      return;
    }
    final PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiLambdaExpression) {
      CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)declarationScope);
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      final PsiStatement body = ((PsiForeachStatement)declarationScope).getBody();
      if (body == null) {
        return;
      }
      BlockUtils.expandSingleStatementToBlockStatement(body);
    }
    final PsiElement body = BlockUtils.getBody(declarationScope);
    if (body == null) {
      return;
    }
    assert body instanceof PsiCodeBlock;
    final String parameterName = parameter.getName();
    final PsiExpression rhs = parameterReference.isValid() ? getRightSideIfLeftSideOfSimpleAssignment(parameterReference, body) : null;
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    final String variableName = javaCodeStyleManager.suggestUniqueVariableName(parameterName, body, true);
    final CommentTracker tracker = new CommentTracker();
    final String initializerText = (rhs == null) ? parameterName : tracker.text(rhs);
    PsiType type = parameter.getType();
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    if (type instanceof PsiLambdaParameterType) {
      return;
    }
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
    if (isOnTheFly()) {
      final PsiLocalVariable variable = (PsiLocalVariable)newStatement.getDeclaredElements()[0];
      final PsiReference[] references = ReferencesSearch.search(variable, variable.getUseScope()).toArray(PsiReference.EMPTY_ARRAY);
      HighlightUtils.showRenameTemplate(body, variable, references);
    }
  }

  private static void replaceReferences(Collection<PsiReferenceExpression> collection, String newVariableName, PsiElement context) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    final PsiReferenceExpression newReference = (PsiReferenceExpression)factory.createExpressionFromText(newVariableName, context);
    for (PsiReferenceExpression reference : collection) {
      reference.replace(newReference);
    }
  }

  private static PsiExpression getRightSideIfLeftSideOfSimpleAssignment(PsiReferenceExpression reference, PsiElement block) {
    if (reference == null) {
      return null;
    }
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(reference);
    if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) {
      return null;
    }
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    if (!JavaTokenType.EQ.equals(tokenType)) {
      return null;
    }
    final PsiExpression lExpression = PsiUtil.skipParenthesizedExprDown(assignmentExpression.getLExpression());
    if (!reference.equals(lExpression)) {
      return null;
    }
    final PsiExpression rExpression = assignmentExpression.getRExpression();
    if (PsiUtil.skipParenthesizedExprDown(rExpression) instanceof PsiAssignmentExpression) {
      return null;
    }
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiExpressionStatement) || grandParent.getParent() != block) {
      return null;
    }
    return rExpression;
  }
}