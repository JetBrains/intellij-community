/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferSimpleWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    final PsiTypeLookupItem lookupItem = item.as(PsiTypeLookupItem.class);
    if (lookupItem != null) {
      return -lookupItem.getBracketsCount();
    }
    if (item.as(CastingLookupElementDecorator.class) != null) {
      return -239;
    }
    return 0;
  }
}