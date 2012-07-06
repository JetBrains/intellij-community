package com.intellij.codeInsight.completion;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PlainPrefixMatcher extends PrefixMatcher {

  public PlainPrefixMatcher(String prefix) {
    super(prefix);
  }

  @Override
  public boolean isStartMatch(String name) {
    return StringUtil.startsWithIgnoreCase(name, getPrefix());
  }

  @Override
  public boolean prefixMatches(@NotNull String name) {
    return StringUtil.containsIgnoreCase(name, getPrefix());
  }

  @NotNull
  @Override
  public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
    return new PlainPrefixMatcher(prefix);
  }
}
