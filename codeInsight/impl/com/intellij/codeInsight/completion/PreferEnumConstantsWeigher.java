/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.psi.PsiEnumConstant;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferEnumConstantsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    return item instanceof MutableLookupElement && ((MutableLookupElement)item).getObject() instanceof PsiEnumConstant;
  }
}
