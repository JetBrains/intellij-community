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
    if (item instanceof PsiTypeLookupItem) {
      return -((PsiTypeLookupItem)item).getBracketsCount();
    }
    return 0;
  }
}