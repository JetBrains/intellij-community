// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.occurrences;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class LocalVariableOccurrenceManager extends BaseOccurrenceManager {
  private final PsiLocalVariable myLocalVariable;

  public LocalVariableOccurrenceManager(PsiLocalVariable localVariable, OccurrenceFilter filter) {
    super(filter);
    myLocalVariable = localVariable;
  }

  @Override
  public PsiExpression @NotNull [] defaultOccurrences() {
    return PsiExpression.EMPTY_ARRAY;
  }

  @Override
  public PsiExpression @NotNull [] findOccurrences() {
    return CodeInsightUtil.findReferenceExpressions(CommonJavaRefactoringUtil.getVariableScope(myLocalVariable), myLocalVariable);
  }


}

