// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * <pre>{@code
 * sb.append(a);
 * sb.append(b);
 * =>
 * sb.append(a).append(b);
 * }</pre>
 */
public class ChainCallJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull final Document document, @NotNull final PsiFile psiFile, final int start, final int end) {
    PsiJavaToken elementAtStartLineEnd = tryCast(psiFile.findElementAt(start), PsiJavaToken.class);
    if (elementAtStartLineEnd == null || !elementAtStartLineEnd.getTokenType().equals(JavaTokenType.SEMICOLON)) return CANNOT_JOIN;
    PsiExpressionStatement secondStatement = PsiTreeUtil.getParentOfType(psiFile.findElementAt(end), PsiExpressionStatement.class);
    if (secondStatement == null) return CANNOT_JOIN;
    PsiMethodCallExpression secondCall = tryCast(secondStatement.getExpression(), PsiMethodCallExpression.class);
    if (secondCall == null) return CANNOT_JOIN;
    PsiElement firstStatement = elementAtStartLineEnd.getParent();

    boolean result = false;
    if (firstStatement instanceof PsiExpressionStatement) {
      if (firstStatement.getParent() != secondStatement.getParent()) return CANNOT_JOIN;
      PsiExpression firstExpression = ((PsiExpressionStatement)firstStatement).getExpression();
      if (firstExpression instanceof PsiMethodCallExpression) {
        result = joinTwoCalls((PsiMethodCallExpression)firstExpression, secondCall);
      } else if (firstExpression instanceof PsiAssignmentExpression) {
        result = joinAssignmentAndCall((PsiAssignmentExpression)firstExpression, secondCall);
      }
    }
    else if (firstStatement instanceof PsiLocalVariable) {
      PsiLocalVariable var = (PsiLocalVariable)firstStatement;
      result = joinExpressionAndCall(var, var.getInitializer(), secondCall);
    }
    if (!result) return CANNOT_JOIN;
    secondStatement.delete();
    return firstStatement.getTextRange().getEndOffset();
  }

  private static boolean joinAssignmentAndCall(PsiAssignmentExpression assignmentExpression, PsiMethodCallExpression nextCall) {
    if (!assignmentExpression.getOperationTokenType().equals(JavaTokenType.EQ)) return false;
    PsiLocalVariable var = ExpressionUtils.resolveLocalVariable(assignmentExpression.getLExpression());
    if (var == null) return false;
    return joinExpressionAndCall(var, assignmentExpression.getRExpression(), nextCall);
  }

  private static boolean joinExpressionAndCall(PsiLocalVariable var, PsiExpression initializer, PsiMethodCallExpression nextCall) {
    if (initializer == null) return false;
    PsiExpression qualifier = getDeepQualifier(nextCall);
    if (!ExpressionUtils.isReferenceTo(qualifier, var)) return false;
    PsiType type = nextCall.getType();
    if (type == null || !type.equals(initializer.getType())) return false;
    if (ReferencesSearch.search(var, new LocalSearchScope(nextCall)).findAll().size() > 1) return false;
    qualifier.replace(initializer);
    initializer.replace(nextCall);
    return true;
  }

  private static boolean joinTwoCalls(PsiMethodCallExpression firstCall,
                                      @NotNull PsiMethodCallExpression secondCall) {

    if (firstCall == null) return false;
    PsiExpression firstQualifier = getDeepQualifier(firstCall);
    if (firstQualifier == null) return false;
    PsiExpression secondQualifier = getDeepQualifier(secondCall);
    if (secondQualifier == null) return false;

    if (!PsiEquivalenceUtil.areElementsEquivalent(firstQualifier, secondQualifier)) return false;
    PsiType type = firstCall.getType();
    if (type == null || !firstCall.getType().equals(firstQualifier.getType())) return false;

    secondQualifier.replace(firstCall);
    firstCall.replace(secondCall);
    return true;
  }

  @Nullable
  static PsiExpression getDeepQualifier(PsiMethodCallExpression firstCall) {
    PsiExpression firstQualifier = firstCall;
    while (firstQualifier instanceof PsiMethodCallExpression) {
      firstQualifier = ((PsiMethodCallExpression)firstQualifier).getMethodExpression().getQualifierExpression();
    }
    return firstQualifier;
  }
}
