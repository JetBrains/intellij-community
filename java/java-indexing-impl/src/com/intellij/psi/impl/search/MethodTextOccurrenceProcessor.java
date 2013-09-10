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
package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.search.RequestResultProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author peter
 */
public final class MethodTextOccurrenceProcessor extends RequestResultProcessor {
  private static final PsiReferenceService ourReferenceService = PsiReferenceService.getService();
  private final PsiMethod[] myMethods;
  private final PsiClass myContainingClass;
  private final boolean myStrictSignatureSearch;

  public MethodTextOccurrenceProcessor(@NotNull final PsiClass aClass, final boolean strictSignatureSearch, final PsiMethod... methods) {
    super(strictSignatureSearch, Arrays.asList(methods));
    myMethods = methods;
    myContainingClass = aClass;
    myStrictSignatureSearch = strictSignatureSearch;
  }

  @Override
  public boolean processTextOccurrence(@NotNull PsiElement element, int offsetInElement, @NotNull final Processor<PsiReference> consumer) {
    for (PsiReference ref : ourReferenceService.getReferences(element, new PsiReferenceService.Hints(myMethods[0], offsetInElement))) {
      if (ReferenceRange.containsOffsetInElement(ref, offsetInElement) && !processReference(consumer, ref)) {
        return false;
      }
    }
    return true;
  }

  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public boolean isStrictSignatureSearch() {
    return myStrictSignatureSearch;
  }

  private boolean processReference(Processor<PsiReference> consumer, PsiReference ref) {
    for (PsiMethod method : myMethods) {
      if (!method.isValid()) {
        continue;
      }

      if (ref instanceof ResolvingHint && !((ResolvingHint)ref).canResolveTo(PsiMethod.class)) {
        return true;
      }
      if (ref.isReferenceTo(method)) {
        return consumer.process(ref);
      }
      PsiElement refElement = ref.resolve();

      for (MethodTextOccurrenceProcessorDelegate delegate : MethodTextOccurrenceProcessorDelegate.EP_NAME.getExtensions()) {
        if (!delegate.processReference(ref, refElement, method, this, consumer)) return false;
      }
    }

    return true;
  }

}
