// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * <pre>{@code
 * x = ...;
 * x = x.a().b().c();
 * =>
 * x = (...).a().b().c();}</pre>
 */
public class AssignmentSequenceJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull final Document document, @NotNull final PsiFile psiFile, final int start, final int end) {
    PsiJavaToken elementAtStartLineEnd = tryCast(psiFile.findElementAt(start), PsiJavaToken.class);
    if (elementAtStartLineEnd == null || !elementAtStartLineEnd.getTokenType().equals(JavaTokenType.SEMICOLON)) return CANNOT_JOIN;
    PsiElement firstElement = elementAtStartLineEnd.getParent();
    PsiExpression firstValue = null;
    PsiVariable variable = null;
    if (firstElement instanceof PsiExpressionStatement) {
      PsiExpressionStatement firstStatement = (PsiExpressionStatement)firstElement;
      PsiAssignmentExpression firstAssignment = ExpressionUtils.getAssignment(firstStatement);
      if (firstAssignment == null) return CANNOT_JOIN;
      PsiReferenceExpression ref = tryCast(PsiUtil.skipParenthesizedExprDown(firstAssignment.getLExpression()), PsiReferenceExpression.class);
      if (ref == null) return CANNOT_JOIN;
      variable = tryCast(ref.resolve(), PsiVariable.class);
      firstValue = firstAssignment.getRExpression();
    } else if (firstElement instanceof PsiLocalVariable) {
      variable = (PsiLocalVariable)firstElement;
      firstValue = variable.getInitializer();
    }
    if (firstValue == null || !(variable instanceof PsiLocalVariable) && !(variable instanceof PsiParameter)) return CANNOT_JOIN;

    PsiExpressionStatement secondStatement = PsiTreeUtil.getParentOfType(psiFile.findElementAt(end), PsiExpressionStatement.class);
    PsiExpression secondValue = ExpressionUtils.getAssignmentTo(secondStatement, variable);
    if (!(secondValue instanceof PsiMethodCallExpression)) return CANNOT_JOIN;
    PsiExpression qualifier = ChainCallJoinLinesHandler.getDeepQualifier((PsiMethodCallExpression)secondValue);
    if (!ExpressionUtils.isReferenceTo(qualifier, variable)) return CANNOT_JOIN;
    if (ReferencesSearch.search(variable, new LocalSearchScope(secondValue)).findAll().size() > 1) return CANNOT_JOIN;
    qualifier.replace(firstValue);
    firstValue.replace(secondValue);
    secondStatement.delete();
    return firstElement.getTextRange().getEndOffset();
  }
}
