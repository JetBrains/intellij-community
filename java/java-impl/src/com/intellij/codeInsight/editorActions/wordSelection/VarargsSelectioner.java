// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class VarargsSelectioner extends AbstractBasicBackBasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiExpressionList;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    if (!(e instanceof PsiExpressionList expressionList)) {
      return Collections.emptyList();
    }

    final PsiParameterList parameterList = getParameterList(expressionList);

    if (parameterList == null) {
      return Collections.emptyList();
    }

    final PsiExpression[] varargArgs = getVarargArgs(parameterList, expressionList);

    if (varargArgs.length == 0) {
      return Collections.emptyList();
    }

    final TextRange firstExpressionRange = varargArgs[0].getTextRange();
    final TextRange lastExpressionRange = varargArgs[varargArgs.length - 1].getTextRange();


    return Collections.singletonList(new TextRange(firstExpressionRange.getStartOffset(), lastExpressionRange.getEndOffset()));
  }

  private static PsiExpression @NotNull [] getVarargArgs(@NotNull PsiParameterList parameterList,
                                                         @NotNull PsiExpressionList expressionList) {
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiExpression[] expressions = expressionList.getExpressions();

    if (parameters.length == 0 || expressions.length == 0) {
      return PsiExpression.EMPTY_ARRAY;
    }

    final int varargIndex = parameters.length - 1;
    final PsiParameter varargParam = parameters[varargIndex];
    if (!varargParam.isVarArgs() || parameters.length > expressions.length) {
      return PsiExpression.EMPTY_ARRAY;
    }

    return Arrays.copyOfRange(expressions, varargIndex, expressions.length);
  }

  @Nullable
  private static PsiParameterList getParameterList(@NotNull PsiExpressionList list) {
    if (!(list.getParent() instanceof PsiMethodCallExpression)) {
      return null;
    }
    final PsiMethod method = ((PsiMethodCallExpression)list.getParent()).resolveMethod();

    return method != null ? method.getParameterList() : null;
  }
}
