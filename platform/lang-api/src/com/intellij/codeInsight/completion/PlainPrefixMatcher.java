package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlainPrefixMatcher extends PrefixMatcher {

  public PlainPrefixMatcher(String prefix) {
    super(prefix);
  }

  @Override
  public boolean prefixMatches(@NotNull String name) {
    final String lowerPrefix = getPrefix().toLowerCase();
    final String lowerName = name.toLowerCase();
    return lowerName.contains(lowerPrefix);
  }

  @NotNull
  @Override
  public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
    return new PlainPrefixMatcher(prefix);
  }
}
