/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;

public class PsiReferenceProcessorAdapter extends ReadActionProcessor<PsiReference> implements Processor<PsiReference> {
  private final PsiReferenceProcessor myProcessor;

  public PsiReferenceProcessorAdapter(final PsiReferenceProcessor processor) {
    myProcessor = processor;
  }

  public boolean processInReadAction(final PsiReference psiReference) {
    return myProcessor.execute(psiReference);
  }
}