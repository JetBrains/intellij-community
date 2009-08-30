/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class MethodTextOccurenceProcessor implements TextOccurenceProcessor {
  private final PsiMethod[] myMethods;
  private final Processor<PsiReference> myConsumer;
  private final PsiClass myContainingClass;
  private final boolean myStrictSignatureSearch;

  public MethodTextOccurenceProcessor(final Processor<PsiReference> consumer, @NotNull final PsiClass aClass, final boolean strictSignatureSearch,
                                      final PsiMethod... methods) {
    myMethods = methods;
    myConsumer = consumer;
    myContainingClass = aClass;
    myStrictSignatureSearch = strictSignatureSearch;
  }

  public boolean execute(PsiElement element, int offsetInElement) {
    final PsiReference[] refs = element.getReferences();
    for (PsiReference ref : refs) {
      if (ref.getRangeInElement().contains(offsetInElement)) {
        for (PsiMethod method : myMethods) {
          if (ref instanceof ResolvingHint && !((ResolvingHint)ref).canResolveTo(method)) {
            return true;
          }
          if (ref.isReferenceTo(method)) {
            return myConsumer.process(ref);
          }
          PsiElement refElement = ref.resolve();

          if (refElement instanceof PsiMethod) {
            PsiMethod refMethod = (PsiMethod)refElement;
            PsiClass refMethodClass = refMethod.getContainingClass();
            if (refMethodClass == null) continue;

            if (!refMethod.hasModifierProperty(PsiModifier.STATIC)) {
              PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(myContainingClass, refMethodClass, PsiSubstitutor.EMPTY);
              if (substitutor != null) {
                if (refMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(substitutor))) {
                  if (!myConsumer.process(ref)) return false;
                }
              }
            }

            if (!myStrictSignatureSearch) {
              PsiManager manager = method.getManager();
              if (manager.areElementsEquivalent(refMethodClass, myContainingClass)) {
                if (!myConsumer.process(ref)) return false;
              }
            }
          }
        }
      }
    }

    return true;
  }
}
