/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.util.occurrences;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.util.RefactoringUtil;
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
    return CodeInsightUtil.findReferenceExpressions(RefactoringUtil.getVariableScope(myLocalVariable), myLocalVariable);
  }


}

