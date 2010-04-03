
/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author peter
*/
public class CamelHumpMatcher extends PrefixMatcher {
  private static int ourLastCompletionCaseSetting = -1;
  private static final Map<String, NameUtil.Matcher> ourPatternCache = new LinkedHashMap<String, NameUtil.Matcher>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, NameUtil.Matcher> eldest) {
      return size() > 10;
    }
  };
  private NameUtil.Matcher myMatcher;
  private final boolean myCaseSensitive;
  private final int currentSetting;

  public CamelHumpMatcher(@NotNull final String prefix) {
    this(prefix, true);
  }

  public CamelHumpMatcher(String prefix, boolean caseSensitive) {
    super(prefix);
    currentSetting = CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE;
    myCaseSensitive = caseSensitive;
  }

  public boolean prefixMatches(@NotNull final String name) {
    synchronized (ourPatternCache) {
      if (myMatcher == null) {
        if (ourLastCompletionCaseSetting != currentSetting) {
          ourPatternCache.clear();
          ourLastCompletionCaseSetting = currentSetting;
        }

        NameUtil.Matcher pattern = ourPatternCache.get(myPrefix);
        if (pattern == null) {
          pattern = createCamelHumpsMatcher();
          ourPatternCache.put(myPrefix, pattern);
        }
        myMatcher = pattern;
      }
      return myMatcher.matches(name);
    }
  }


  public boolean prefixMatches(@NotNull final LookupElement element) {
    final LookupItem item = element.as(LookupItem.class); //must die, use LookupElementBuilder or CompletionResultSet.caseInsensitive
    final LookupElementBuilder builder = element.as(LookupElementBuilder.class);
    boolean itemCaseInsensitive = item != null && Boolean.TRUE.equals(item.getAttribute(LookupItem.CASE_INSENSITIVE)) ||
                                  builder != null && !builder.isCaseSensitive();

    return prefixMatchersInternal(element, itemCaseInsensitive);
  }

  private boolean prefixMatchersInternal(final LookupElement element, final boolean itemCaseInsensitive) {
    for (final String name : element.getAllLookupStrings()) {
      if (itemCaseInsensitive && StringUtil.startsWithIgnoreCase(name, myPrefix) || prefixMatches(name)) {
        return true;
      }
      if (itemCaseInsensitive && CodeInsightSettings.ALL != CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE && name.length() > 0) {
        final char c = name.charAt(0);
        String swappedCase = (Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c)) + name.substring(1);
        if (prefixMatches(swappedCase)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public PrefixMatcher cloneWithPrefix(@NotNull final String prefix) {
    return new CamelHumpMatcher(prefix);
  }

  private NameUtil.Matcher createCamelHumpsMatcher() {
    if (!myCaseSensitive) {
      return NameUtil.buildMatcher(myPrefix, 0, true, true);
    }

    switch (CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE) {
      case CodeInsightSettings.NONE:
        return NameUtil.buildMatcher(myPrefix, 0, true, true);
      case CodeInsightSettings.FIRST_LETTER:
        int exactPrefixLen = myPrefix.startsWith("*") ? 0 : 1;
        return NameUtil.buildMatcher(myPrefix, exactPrefixLen, true, true);
      case CodeInsightSettings.ALL:
        return NameUtil.buildMatcher(myPrefix, 0, false, false);
      default:
        return NameUtil.buildMatcher(myPrefix, 0, true, false);
    }
  }

  @Override
  public String toString() {
    return myPrefix;
  }
}
