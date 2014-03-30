package com.intellij.codeInspection.inheritance.search;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class InheritorsStatisticsSearchResult {

  @NotNull
  private final PsiClass myClass;
  private final int myPercent;

  InheritorsStatisticsSearchResult(final @NotNull PsiClass aClass, final int percent) {
    myClass = aClass;
    myPercent = percent;
  }

  public PsiClass getPsiClass() {
    return myClass;
  }

  public int getPercent() {
    return myPercent;
  }

}
