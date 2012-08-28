package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class PrefixMatcher {
  public static final PrefixMatcher ALWAYS_TRUE = new PlainPrefixMatcher("");
  protected final String myPrefix;

  protected PrefixMatcher(String prefix) {
    myPrefix = prefix;
  }

  public boolean prefixMatches(@NotNull LookupElement element) {
    for (String s : element.getAllLookupStrings()) {
      if (prefixMatches(s)) {
        return true;
      }
    }
    return false;
  }

  public boolean isStartMatch(LookupElement element) {
    for (String s : element.getAllLookupStrings()) {
      if (isStartMatch(s)) {
        return true;
      }
    }
    return false;
  }

  public boolean isStartMatch(String name) {
    return prefixMatches(name);
  }

  public abstract boolean prefixMatches(@NotNull String name);

  @NotNull
  public final String getPrefix() {
    return myPrefix;
  }

  @NotNull public abstract PrefixMatcher cloneWithPrefix(@NotNull String prefix);

  public int matchingDegree(String string) {
    return 0;
  }
}
