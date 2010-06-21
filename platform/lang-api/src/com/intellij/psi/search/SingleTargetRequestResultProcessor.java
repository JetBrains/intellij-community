package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.ReferenceRange;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class SingleTargetRequestResultProcessor extends RequestResultProcessor {
  private static final PsiReferenceService ourReferenceService = PsiReferenceService.getService();
  private final PsiElement myTarget;

  public SingleTargetRequestResultProcessor(@NotNull PsiElement target) {
    myTarget = target;
  }

  public boolean processTextOccurrence(PsiElement element, int offsetInElement, final Processor<PsiReference> consumer) {
    for (PsiReference ref : ourReferenceService.getReferences(element, new PsiReferenceService.Hints(myTarget, offsetInElement))) {
      if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
        if (ref.isReferenceTo(myTarget)) {
          if (!consumer.process(ref)) {
            return false;
          }
        }
      }
    }
    return true;
  }

}
