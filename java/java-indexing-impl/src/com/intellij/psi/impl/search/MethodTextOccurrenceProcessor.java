// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public class MethodTextOccurrenceProcessor extends RequestResultProcessor {
  private static final PsiReferenceService ourReferenceService = PsiReferenceService.getService();
  private final PsiMethod[] myMethods;
  protected final PsiClass myContainingClass;
  protected final boolean myStrictSignatureSearch;

  public MethodTextOccurrenceProcessor(@NotNull final PsiClass aClass, final boolean strictSignatureSearch, final PsiMethod... methods) {
    super(strictSignatureSearch, Arrays.asList(methods));
    myMethods = methods;
    myContainingClass = aClass;
    myStrictSignatureSearch = strictSignatureSearch;
  }

  @Override
  public final boolean processTextOccurrence(@NotNull PsiElement element, int offsetInElement, @NotNull final Processor<? super PsiReference> consumer) {
    for (PsiReference ref : ourReferenceService.getReferences(element, new PsiReferenceService.Hints(myMethods[0], offsetInElement))) {
      if (ReferenceRange.containsOffsetInElement(ref, offsetInElement) && !processReference(consumer, ref)) {
        return false;
      }
    }
    return true;
  }

  private boolean processReference(Processor<? super PsiReference> consumer, PsiReference ref) {
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

      if (!processInexactReference(ref, ref.resolve(), method, consumer)) {
        return false;
      }
    }

    return true;
  }

  protected boolean processInexactReference(PsiReference ref, PsiElement refElement, PsiMethod method, Processor<? super PsiReference> consumer) {
    if (refElement instanceof PsiMethod) {
      PsiMethod refMethod = (PsiMethod)refElement;
      PsiClass refMethodClass = refMethod.getContainingClass();
      if (refMethodClass == null) return true;

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

    return true;
  }

}
