/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;

public class MoveRenameUsageInfo extends UsageInfo{
  public final PsiElement referencedElement;
  public final PsiReference reference;

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, PsiElement referencedElement){
    super(element);
    this.referencedElement = referencedElement;
    if (reference == null) {
      this.reference = element.getReference();
    }
    else {
      this.reference = reference;
    }
  }

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, int startOffset, int endOffset, PsiElement referencedElement, boolean nonCodeUsage){
    super(element, startOffset, endOffset, nonCodeUsage);
    this.referencedElement = referencedElement;
    if (reference == null) {
      this.reference = element.getReference();
    }
    else {
      this.reference = reference;
    }
  }
}
