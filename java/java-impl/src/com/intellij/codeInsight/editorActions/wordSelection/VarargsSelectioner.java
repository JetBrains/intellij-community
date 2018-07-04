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
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Danila Ponomarenko
 */
public class VarargsSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof PsiExpressionList;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    if (!(e instanceof PsiExpressionList)) {
      return Collections.emptyList();
    }

    final PsiExpressionList expressionList = (PsiExpressionList)e;
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

  @NotNull
  private static PsiExpression[] getVarargArgs(@NotNull PsiParameterList parameterList,
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
