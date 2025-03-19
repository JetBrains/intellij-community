// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;


public class PlainPrefixMatcher extends PrefixMatcher {

  private final boolean myPrefixMatchesOnly;

  public PlainPrefixMatcher(@NotNull String prefix) {
    this(prefix, false);
  }

  public PlainPrefixMatcher(@NotNull String prefix, boolean prefixMatchesOnly) {
    super(prefix);
    myPrefixMatchesOnly = prefixMatchesOnly;
  }

  @Override
  public boolean isStartMatch(@NotNull String name) {
    return StringUtil.startsWithIgnoreCase(name, getPrefix());
  }

  @Override
  public boolean prefixMatches(@NotNull String name) {
    if (myPrefixMatchesOnly) {
      return isStartMatch(name);
    }
    return StringUtil.containsIgnoreCase(name, getPrefix());
  }

  @Override
  public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
    return new PlainPrefixMatcher(prefix, myPrefixMatchesOnly);
  }
}
