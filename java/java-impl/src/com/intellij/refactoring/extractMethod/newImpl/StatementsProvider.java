// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StatementsProvider {

  public List<PsiElement> getStatementsFromRange(PsiFile file, TextRange range){
    final PsiExpression expression = CodeInsightUtil.findExpressionInRange(file, range.getStartOffset(), range.getEndOffset());
    if (expression != null) return Collections.singletonList(expression);
    final PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, range.getStartOffset(), range.getEndOffset());
    return Arrays.asList(statements);
  }
}
