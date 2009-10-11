/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        assert variants != null : reference;
        for (final Object variant : variants) {
          if (!processor.process(CompletionData.objectToLookupItem(variant))) return false;
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
