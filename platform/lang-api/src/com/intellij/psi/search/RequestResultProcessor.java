package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author peter
 */
public abstract class RequestResultProcessor {
  private final Object myEquality;

  protected RequestResultProcessor(@NotNull Object... equality) {
    myEquality = Arrays.asList(equality);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RequestResultProcessor that = (RequestResultProcessor)o;

    if (!myEquality.equals(that.myEquality)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myEquality.hashCode();
  }

  public abstract boolean processTextOccurrence(PsiElement element, int offsetInElement, final Processor<PsiReference> consumer);

}
