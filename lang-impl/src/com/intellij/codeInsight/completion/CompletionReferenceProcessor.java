/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.util.Processor;

/**
 * @author peter
 */
public class CompletionReferenceProcessor {
  public static final CompletionReferenceProcessor INSTANCE = new CompletionReferenceProcessor();

  protected CompletionReferenceProcessor() {
  }

  public final boolean processReferences(CompletionParameters parameters, Processor<LookupElement> processor) {
    final PsiReference reference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
    return reference == null || processReferenceVariants(reference, createReferenceProcessor(processor));
  }

  protected Processor<PsiReference> createReferenceProcessor(final Processor<LookupElement> processor) {
    return new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        final Object[] variants = reference.getVariants();
        if (variants != null) {
          for (final Object variant : variants) {
            if (!processor.process(CompletionData.objectToLookupItem(variant))) return false;
          }
        }
        return true;
      }
    };
  }

  public boolean processReferenceVariants(PsiReference reference, Processor<PsiReference> processor) {
    if (reference instanceof PsiMultiReference) {
      return processMultiReference((PsiMultiReference)reference, processor);
    }

    return processor.process(reference);
  }

  protected boolean processMultiReference(final PsiMultiReference multiReference, Processor<PsiReference> processor) {
    final PsiReference[] references = multiReference.getReferences();
    boolean hasHard = hasHardReference(references);
    for (final PsiReference reference : references) {
      if (!(hasHard && reference.isSoft())) {
        if (!processReferenceVariants(reference, processor)) return false;
      }
    }

    return true;
  }

  protected static boolean hasHardReference(final PsiReference[] references) {
    for (final PsiReference reference : references) {
      if (!reference.isSoft()) {
        return true;
      }
    }
    return false;
  }


}
