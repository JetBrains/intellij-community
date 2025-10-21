// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;

public class StartOnlyMatcher extends PrefixMatcher {
  private final PrefixMatcher myDelegate;

  public StartOnlyMatcher(PrefixMatcher delegate) {
    super(delegate.getPrefix());
    myDelegate = delegate;
  }

  @Override
  public boolean isStartMatch(@NotNull String name) {
    return myDelegate.isStartMatch(name);
  }

  @Override
  public boolean prefixMatches(@NotNull String name) {
    return myDelegate.prefixMatches(name) && myDelegate.isStartMatch(name);
  }

  @Override
  public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
    return new StartOnlyMatcher(myDelegate.cloneWithPrefix(prefix));
  }
}
