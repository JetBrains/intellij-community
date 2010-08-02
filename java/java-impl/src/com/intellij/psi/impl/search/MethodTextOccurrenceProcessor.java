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
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
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

  public boolean processTextOccurrence(PsiElement element, int offsetInElement, final Processor<PsiReference> consumer) {
    for (PsiReference ref : ourReferenceService.getReferences(element, new PsiReferenceService.Hints(myMethods[0], offsetInElement))) {
      if (ReferenceRange.containsOffsetInElement(ref, offsetInElement) && !processReference(consumer, ref)) {
        return false;
      }
    }
    return true;
  }


  private boolean processReference(Processor<PsiReference> consumer, PsiReference ref) {
    for (PsiMethod method : myMethods) {
      if (!method.isValid()) {
        continue;
      }

      if (ref instanceof ResolvingHint && !((ResolvingHint)ref).canResolveTo(method)) {
        return true;
      }
      if (ref.isReferenceTo(method)) {
        return consumer.process(ref);
      }
      PsiElement refElement = ref.resolve();

      if (refElement instanceof PsiMethod) {
        PsiMethod refMethod = (PsiMethod)refElement;
        PsiClass refMethodClass = refMethod.getContainingClass();
        if (refMethodClass == null) continue;

        if (!refMethod.hasModifierProperty(PsiModifier.STATIC)) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(myContainingClass, refMethodClass, PsiSubstitutor.EMPTY);
          if (substitutor != null) {
            MethodSignature superSignature = method.getSignature(substitutor);
            MethodSignature refSignature = refMethod.getSignature(PsiSubstitutor.EMPTY);

            if (MethodSignatureUtil.isSubsignature(superSignature, refSignature)) {
              if (!consumer.process(ref)) return false;
            }
          }
        }

        if (!myStrictSignatureSearch) {
          PsiManager manager = method.getManager();
          if (manager.areElementsEquivalent(refMethodClass, myContainingClass)) {
            if (!consumer.process(ref)) return false;
          }
        }
      }
    }

    return true;
  }

}
