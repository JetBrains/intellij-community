package com.intellij.codeInspection.inheritance.search;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
class InheritorsCountData implements Comparable<InheritorsCountData> {
  @NotNull
  private final PsiClass myPsiClass;
  private final int myInheritorsCount;

  public InheritorsCountData(@NotNull final PsiClass psiClass, final int inheritorsCount) {
    myPsiClass = psiClass;
    myInheritorsCount = inheritorsCount;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof InheritorsCountData)) return false;

    final InheritorsCountData data = (InheritorsCountData)o;
    return myInheritorsCount == data.myInheritorsCount && myPsiClass.equals(data.myPsiClass);
  }

  @NotNull
  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  public int getInheritorsCount() {
    return myInheritorsCount;
  }

  @Override
  public int hashCode() {
    final String name = myPsiClass.getName();
    int result = name != null ? name.hashCode() : 0;
    return 31 * result + myInheritorsCount;
  }

  @Override
  public int compareTo(@NotNull final InheritorsCountData that) {
    final int sub = -this.myInheritorsCount + that.myInheritorsCount;
    if (sub != 0) return sub;
    return String.CASE_INSENSITIVE_ORDER.compare(this.myPsiClass.getName(), that.myPsiClass.getName());
  }

  public String toString() {
    return String.format("%s:%d", myPsiClass, myInheritorsCount);
  }
}