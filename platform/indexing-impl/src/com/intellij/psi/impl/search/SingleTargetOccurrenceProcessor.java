// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.model.SymbolService;
import com.intellij.model.search.TextOccurrence;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.PsiReferenceService.Hints;
import com.intellij.psi.ReferenceRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

final class SingleTargetOccurrenceProcessor implements Function<TextOccurrence, Collection<? extends SymbolReference>> {

  private static final PsiReferenceService ourReferenceService = PsiReferenceService.getService();
  private final Symbol myTarget;

  SingleTargetOccurrenceProcessor(@NotNull Symbol target) {
    myTarget = target;
  }

  @Override
  public Collection<? extends SymbolReference> apply(TextOccurrence occurrence) {
    final PsiElement element = occurrence.getElement();
    final int offsetInElement = occurrence.getOffsetInElement();
    final Hints hints = new Hints(SymbolService.getPsiElement(myTarget), offsetInElement);
    final List<PsiReference> references = ourReferenceService.getReferences(element, hints);
    return ContainerUtil.filter(references, reference -> {
      ProgressManager.checkCanceled();
      if (!ReferenceRange.containsOffsetInElement(reference, offsetInElement)) return false;
      if (!reference.references(myTarget)) return false;
      return true;
    });
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SingleTargetOccurrenceProcessor processor = (SingleTargetOccurrenceProcessor)o;

    if (!myTarget.equals(processor.myTarget)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myTarget.hashCode();
  }
}
