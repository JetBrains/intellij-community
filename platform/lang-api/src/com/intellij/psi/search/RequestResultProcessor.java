package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ReferenceRange;
import com.intellij.util.Processor;

/**
 * @author peter
 */
public abstract class RequestResultProcessor {

  public boolean execute(PsiElement element, int offsetInElement, final Processor<PsiReference> consumer) {
    for (PsiReference ref : element.getReferences()) {
      if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
        if (!processReference(consumer, ref)) {
          return false;
        }
      }
    }
    return true;
  }

  protected abstract boolean processReference(Processor<PsiReference> consumer, PsiReference ref);

}
