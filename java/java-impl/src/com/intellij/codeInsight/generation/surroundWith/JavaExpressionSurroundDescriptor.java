// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
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
      if (expr == null || expr.isPhysical()) {
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
      list.addAll(JavaExpressionSurrounder.EP_NAME.getExtensionList());
      mySurrounders = list.toArray(Surrounder.EMPTY_ARRAY);
    }
    return mySurrounders;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }
}
