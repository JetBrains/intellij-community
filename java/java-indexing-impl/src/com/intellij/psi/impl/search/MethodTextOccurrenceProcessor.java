// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.psi.*;
import com.intellij.psi.search.RequestResultProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Supplier;

public class MethodTextOccurrenceProcessor extends RequestResultProcessor {
  private static final Supplier<PsiReferenceService> ourReferenceService = CachedSingletonsRegistry.lazy(
    () -> PsiReferenceService.getService()
  );
  private final PsiMethod[] myMethods;
  protected final PsiClass myContainingClass;
  private final boolean myStrictSignatureSearch;

  public MethodTextOccurrenceProcessor(final @NotNull PsiClass aClass,
                                       final boolean strictSignatureSearch,
                                       PsiMethod @NotNull ... methods) {
    super(strictSignatureSearch, Arrays.asList(methods));
    myMethods = methods;
    myContainingClass = aClass;
    myStrictSignatureSearch = strictSignatureSearch;
  }

  @Override
  public final boolean processTextOccurrence(@NotNull PsiElement element,
                                             int offsetInElement,
                                             final @NotNull Processor<? super PsiReference> consumer) {
    for (PsiReference ref : ourReferenceService.get().getReferences(element, new PsiReferenceService.Hints(myMethods[0], offsetInElement))) {
      if (ReferenceRange.containsOffsetInElement(ref, offsetInElement) && !processReference(consumer, ref)) {
        return false;
      }
    }
    return true;
  }

  private boolean processReference(@NotNull Processor<? super PsiReference> consumer, @NotNull PsiReference ref) {
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

  protected boolean processInexactReference(@NotNull PsiReference ref,
                                            @Nullable PsiElement refElement,
                                            @NotNull PsiMethod method,
                                            @NotNull Processor<? super PsiReference> consumer) {
    if (refElement instanceof PsiMethod refMethod) {
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
          return consumer.process(ref);
        }
      }
    }

    return true;
  }
}
