/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;

/**
 * @author ven
 */
public class JavaExpressionSurroundDescriptor implements SurroundDescriptor {
  private Surrounder[] mySurrounders;

  private static final Surrounder[] SURROUNDERS = {
    new JavaWithParenthesesSurrounder(),
      new JavaWithCastSurrounder(),
      new JavaWithNotSurrounder(),
      new JavaWithNotInstanceofSurrounder(),
      new JavaWithIfExpressionSurrounder(),
      new JavaWithIfElseExpressionSurrounder(),
      new JavaWithNullCheckSurrounder()
  };

  @Override
  @NotNull public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr == null) {
      expr = IntroduceVariableBase.getSelectedExpression(file.getProject(), file, startOffset, endOffset);
      if (expr == null) {
        return PsiElement.EMPTY_ARRAY;
      }
    }
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression");
    return new PsiElement[] {expr};
  }

  @Override
  @NotNull public Surrounder[] getSurrounders() {
    if (mySurrounders == null) {
      final ArrayList<Surrounder> list = new ArrayList<>();
      Collections.addAll(list, SURROUNDERS);
      Collections.addAll(list, Extensions.getExtensions(JavaExpressionSurrounder.EP_NAME));
      mySurrounders = list.toArray(new Surrounder[list.size()]);
    }
    return mySurrounders;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }
}
