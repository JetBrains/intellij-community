package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class JavaStatementsSurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] SURROUNDERS  = new Surrounder[] {
    new JavaWithIfSurrounder(),
      new JavaWithIfElseSurrounder(),
      new JavaWithWhileSurrounder(),
      new JavaWithDoWhileSurrounder(),
      new JavaWithForSurrounder(),

      new JavaWithTryCatchSurrounder(),
      new JavaWithTryFinallySurrounder(),
      new JavaWithTryCatchFinallySurrounder(),
      new JavaWithSynchronizedSurrounder(),
      new JavaWithRunnableSurrounder(),

      new JavaWithBlockSurrounder()
  };

  @NotNull public Surrounder[] getSurrounders() {
    return SURROUNDERS;
  }

  @NotNull public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    final PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements.length == 0) return PsiElement.EMPTY_ARRAY;
    FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassists.surroundwith.statement");
    return statements;
  }
}
