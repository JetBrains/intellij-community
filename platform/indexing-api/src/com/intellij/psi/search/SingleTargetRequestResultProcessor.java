// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.ReferenceRange;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SingleTargetRequestResultProcessor extends RequestResultProcessor {
  private final PsiElement myTarget;
  private final PsiReferenceService myService = PsiReferenceService.getService();

  public SingleTargetRequestResultProcessor(@NotNull PsiElement target) {
    super(target);
    myTarget = target;
  }

  @Override
  public boolean processTextOccurrence(@NotNull PsiElement element,
                                       int offsetInElement,
                                       @NotNull Processor<? super PsiReference> consumer) {
    if (!myTarget.isValid()) {
      return false;
    }

    PsiReferenceService.Hints hints = new PsiReferenceService.Hints(myTarget, offsetInElement);
    List<PsiReference> references = myService.getReferences(element, hints);
    for (int i = 0; i < references.size(); i++) {
      PsiReference ref = references.get(i);
      ProgressManager.checkCanceled();
      if (ReferenceRange.containsOffsetInElement(ref, offsetInElement) && ref.isReferenceTo(myTarget) && !consumer.process(ref)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return "SingleTarget: " + myTarget;
  }
}
