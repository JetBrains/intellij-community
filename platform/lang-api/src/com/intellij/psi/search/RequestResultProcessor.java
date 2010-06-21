package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;

/**
 * @author peter
 */
public abstract class RequestResultProcessor {

  public abstract boolean processTextOccurrence(PsiElement element, int offsetInElement, final Processor<PsiReference> consumer);

}
