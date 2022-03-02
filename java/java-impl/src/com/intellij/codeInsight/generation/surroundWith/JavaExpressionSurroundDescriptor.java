// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.IntroduceVariableUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class JavaExpressionSurroundDescriptor implements SurroundDescriptor {

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
  public PsiElement @NotNull [] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr == null) {
      expr = IntroduceVariableUtil.getSelectedExpression(file.getProject(), file, startOffset, endOffset);
      if (expr == null || expr.isPhysical()) {
        return PsiElement.EMPTY_ARRAY;
      }
    }
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression");
    return new PsiElement[] {expr};
  }

  @Override
  public Surrounder @NotNull [] getSurrounders() {
    List<JavaExpressionSurrounder> extensionList = JavaExpressionSurrounder.EP_NAME.getExtensionList();
    final ArrayList<Surrounder> list = new ArrayList<>(SURROUNDERS.length + extensionList.size());
    Collections.addAll(list, SURROUNDERS);
    list.addAll(extensionList);
    return list.toArray(Surrounder.EMPTY_ARRAY);
  }

  @Override
  public boolean isExclusive() {
    return false;
  }
}
