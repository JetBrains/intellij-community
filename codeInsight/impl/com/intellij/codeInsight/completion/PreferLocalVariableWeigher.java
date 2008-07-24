/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiThisExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferLocalVariableWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.SMART) return 0;
    if (!(item instanceof MutableLookupElement)) return 0;

    final Object object = ((MutableLookupElement)item).getObject();
    return object instanceof PsiLocalVariable || object instanceof PsiParameter || object instanceof PsiThisExpression;
  }
}
