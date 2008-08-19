/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class BasicToClassNameDelegator extends CompletionContributor{

  @Override
  public boolean fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) return true;

    final PsiFile file = parameters.getOriginalFile();
    if (!(file instanceof PsiJavaFile)) return true;

    final PsiElement position = parameters.getPosition();
    if (!(position.getParent() instanceof PsiJavaCodeReferenceElement)) return true;

    final Ref<Boolean> empty = Ref.create(true);
    CompletionService.getCompletionService().getVariantsFromContributors(EP_NAME, parameters, this, new Consumer<LookupElement>() {
          public void consume(final LookupElement lookupElement) {
            empty.set(false);
            result.addElement(lookupElement);
          }
        });

    final CompletionParameters classParams;

    final int invocationCount = parameters.getInvocationCount();
    final int offset = parameters.getOffset();
    if (empty.get().booleanValue()) {
      classParams = new CompletionParameters(position, file, CompletionType.CLASS_NAME, offset, invocationCount);
    }
    else if (invocationCount > 1) {
      classParams = new CompletionParameters(position, file, CompletionType.CLASS_NAME, offset, invocationCount - 1);
    } else {
      return false;
    }


    CompletionService.getCompletionService().getVariantsFromContributors(EP_NAME, classParams, null, new Consumer<LookupElement>() {
      public void consume(final LookupElement lookupElement) {
        result.addElement(lookupElement);
      }
    });

    return false;
  }

}
