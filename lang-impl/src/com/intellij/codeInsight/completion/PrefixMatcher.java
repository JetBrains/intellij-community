/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface PrefixMatcher {

  boolean prefixMatches(@NotNull LookupElement element);

  boolean prefixMatches(@NotNull String name);

  @NotNull String getPrefix();
}
