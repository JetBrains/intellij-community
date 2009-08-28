package com.intellij.refactoring.util;

import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public abstract class RelatedUsageInfo extends MoveRenameUsageInfo {
  private final PsiElement myRelatedElement;

  public RelatedUsageInfo(PsiReference reference, PsiElement referencedElement, PsiElement relatedElement) {
    super(reference, referencedElement);
    myRelatedElement = relatedElement;
  }

  protected RelatedUsageInfo(final PsiElement element, final PsiReference reference, final PsiElement referencedElement, final PsiElement relatedElement) {
    super(element, reference, referencedElement);
    myRelatedElement = relatedElement;
  }

  public PsiElement getRelatedElement() {
    return myRelatedElement;
  }
}
