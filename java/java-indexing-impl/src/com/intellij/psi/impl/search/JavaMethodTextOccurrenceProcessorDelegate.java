package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;

public class JavaMethodTextOccurrenceProcessorDelegate implements MethodTextOccurrenceProcessorDelegate {
  @Override
  public boolean processReference(PsiReference ref,
                                  PsiElement refElement,
                                  PsiMethod method,
                                  MethodTextOccurrenceProcessor processor,
                                  Processor<PsiReference> consumer) {
    if (refElement instanceof PsiMethod) {
      PsiClass containingClass = processor.getContainingClass();

      PsiMethod refMethod = (PsiMethod)refElement;
      PsiClass refMethodClass = refMethod.getContainingClass();
      if (refMethodClass == null) return true;

      if (!refMethod.hasModifierProperty(PsiModifier.STATIC)) {
        PsiSubstitutor substitutor =
          TypeConversionUtil.getClassSubstitutor(containingClass, refMethodClass, PsiSubstitutor.EMPTY);
        if (substitutor != null) {
          MethodSignature superSignature = method.getSignature(substitutor);
          MethodSignature refSignature = refMethod.getSignature(PsiSubstitutor.EMPTY);

          if (MethodSignatureUtil.isSubsignature(superSignature, refSignature)) {
            if (!consumer.process(ref)) return false;
          }
        }
      }

      if (!processor.isStrictSignatureSearch()) {
        PsiManager manager = method.getManager();
        if (manager.areElementsEquivalent(refMethodClass, containingClass)) {
          if (!consumer.process(ref)) return false;
        }
      }
    }

    return true;
  }
}
