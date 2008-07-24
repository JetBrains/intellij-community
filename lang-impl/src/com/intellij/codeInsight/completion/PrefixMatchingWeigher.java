/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PrefixMatchingWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    if (location.getCompletionType() == CompletionType.CLASS_NAME) return 0;

    final String lookupString = item.getLookupString();
    return (StringUtil.capitalsOnly(lookupString).startsWith(StringUtil.capitalsOnly(item.getPrefixMatcher().getPrefix())) ? 4 : 0) +
           (lookupString.startsWith(item.getPrefixMatcher().getPrefix()) ? 2 : 0) +
           (StringUtil.startsWithIgnoreCase(lookupString, item.getPrefixMatcher().getPrefix()) ? 1 : 0);
  }
}
