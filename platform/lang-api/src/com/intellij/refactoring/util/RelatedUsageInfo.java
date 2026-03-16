// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;


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
