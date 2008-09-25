/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
    final boolean isJava = file.getLanguage() == StdLanguages.JAVA;
    if (!isJava && !(file.getLanguage() instanceof XMLLanguage)) return true;

    final PsiElement position = parameters.getPosition();
    if (isJava) {
      if (!(position.getParent() instanceof PsiJavaCodeReferenceElement)) return true;
      if (((PsiJavaCodeReferenceElement)position.getParent()).getQualifier() != null) return true;
    }

    final String s = result.getPrefixMatcher().getPrefix();
    if (StringUtil.isEmpty(s) || !Character.isUpperCase(s.charAt(0))) return true;

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
        if (lookupElement instanceof JavaPsiClassReferenceElement) {
          ((JavaPsiClassReferenceElement)lookupElement).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        }
        result.addElement(lookupElement);
      }
    });

    return false;
  }

}
