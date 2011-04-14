package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.ReferenceRange;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public final class SingleTargetRequestResultProcessor extends RequestResultProcessor {
  private static final PsiReferenceService ourReferenceService = PsiReferenceService.getService();
  private final PsiElement myTarget;

  public SingleTargetRequestResultProcessor(@NotNull PsiElement target) {
    super(target);
    myTarget = target;
  }

  public boolean processTextOccurrence(PsiElement element, int offsetInElement, final Processor<PsiReference> consumer) {
    final List<PsiReference> references = ourReferenceService.getReferences(element,
                                                                            new PsiReferenceService.Hints(myTarget, offsetInElement));
    for (PsiReference ref : references) {
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

  @Override
  public String toString() {
    return "SingleTarget: " + myTarget;
  }
}
