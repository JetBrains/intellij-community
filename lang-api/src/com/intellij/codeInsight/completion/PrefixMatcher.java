/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PrefixMatcher {
  public static final PrefixMatcher FALSE_MATCHER = new PrefixMatcher() {
    public boolean prefixMatches(@NotNull final LookupElement element) {
      return false;
    }

    public boolean prefixMatches(@NotNull final String name) {
      return false;
    }

    @NotNull
    public String getPrefix() {
      throw new UnsupportedOperationException("Method getPrefix is not yet implemented in " + getClass().getName());
    }

    @NotNull
    public PrefixMatcher cloneWithPrefix(@NotNull final String prefix) {
      return this;
    }
  };


  public abstract boolean prefixMatches(@NotNull LookupElement element);

  public abstract boolean prefixMatches(@NotNull String name);

  @NotNull
  public abstract String getPrefix();

  @NotNull public abstract PrefixMatcher cloneWithPrefix(@NotNull String prefix);
}
