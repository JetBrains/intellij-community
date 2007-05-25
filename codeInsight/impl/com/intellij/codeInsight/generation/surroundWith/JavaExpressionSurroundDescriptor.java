package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;

/**
 * @author ven
 */
public class JavaExpressionSurroundDescriptor implements SurroundDescriptor {
  private Surrounder[] mySurrounders = null;

  private static final Surrounder[] SURROUNDERS = {
    new JavaWithParenthesesSurrounder(),
      new JavaWithCastSurrounder(),
      new JavaWithNotSurrounder(),
      new JavaWithNotInstanceofSurrounder(),
      new JavaWithIfExpressionSurrounder(),
      new JavaWithIfElseExpressionSurrounder()
  };

  @NotNull public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr == null) return PsiElement.EMPTY_ARRAY;
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.expression");
    return new PsiElement[] {expr};
  }

  @NotNull public Surrounder[] getSurrounders() {
    if (mySurrounders == null) {
      final ArrayList<Surrounder> list = new ArrayList<Surrounder>();
      Collections.addAll(list, SURROUNDERS);
      Collections.addAll(list, Extensions.getExtensions(JavaExpressionSurrounder.EP_NAME));
      mySurrounders = list.toArray(new Surrounder[list.size()]);
    }
    return mySurrounders;
  }
}
