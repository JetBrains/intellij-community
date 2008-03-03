/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PrefixMatchingWeigher extends CompletionWeigher {
  private static final NotNullLazyKey<String, CompletionLocation> PREFIX_CAPITALS = NotNullLazyKey.create("prefixCapitals", new NotNullFunction<CompletionLocation, String>() {
    @NotNull
    public String fun(final CompletionLocation location) {
      return StringUtil.capitalsOnly(location.getPrefix());
    }
  });

  public Comparable weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
    return (StringUtil.capitalsOnly(item.getLookupString()).startsWith(PREFIX_CAPITALS.getValue(location)) ? 4 : 0) +
           (item.getLookupString().startsWith(location.getPrefix()) ? 2 : 0) +
           (StringUtil.startsWithIgnoreCase(item.getLookupString(), location.getPrefix()) ? 1 : 0);
  }
}
