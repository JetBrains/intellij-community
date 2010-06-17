package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
public class SingleTargetRequestResultProcessor extends RequestResultProcessor {
  private final PsiElement myTarget;

  public SingleTargetRequestResultProcessor(@NotNull PsiElement target) {
    myTarget = target;
  }

  protected boolean processReference(Processor<PsiReference> consumer, PsiReference ref) {
    if (ref.isReferenceTo(myTarget)) {
      return consumer.process(ref);
    }
    return true;
  }

  protected List<PsiReference> getReferencesInElement(PsiElement element, int offsetInElement) {
    return PsiReferenceService.getService().getReferences(element, new PsiReferenceService.Hints(myTarget, offsetInElement));
  }
}
