package com.intellij.psi.impl.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;

public interface MethodTextOccurrenceProcessorDelegate {
  ExtensionPointName<MethodTextOccurrenceProcessorDelegate> EP_NAME =
    ExtensionPointName.create("com.intellij.methodTextOccurrenceProcessorDelegate");

  boolean processReference(
    PsiReference ref,
    PsiElement refElement,
    PsiMethod method,
    MethodTextOccurrenceProcessor processor,
    Processor<PsiReference> consumer
  );
}
