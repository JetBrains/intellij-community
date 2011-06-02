
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author peter
*/
public class CamelHumpMatcher extends PrefixMatcher {
  private static int ourLastCompletionCaseSetting = -1;
  private static final Map<String, Matcher> ourPatternCache = new LinkedHashMap<String, Matcher>() {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Matcher> eldest) {
      return size() > 10;
    }
  };
  private Matcher myMatcher;
  private final boolean myCaseSensitive;
  private final int currentSetting;
  private final boolean myRelaxedMatching;

  public CamelHumpMatcher(@NotNull final String prefix) {
    this(prefix, true, false);
  }

  public CamelHumpMatcher(String prefix, boolean caseSensitive, boolean relaxedMatching) {
    super(prefix);
    myRelaxedMatching = relaxedMatching;
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

        myMatcher = obtainMatcher(myRelaxedMatching, myCaseSensitive);
      }
      if (myMatcher.matches(name)) {
        if (myRelaxedMatching && obtainMatcher(false, myCaseSensitive).matches(name)) {
          return false;
        }

        return true;
      }
      return false;
    }
  }

  private Matcher obtainMatcher(final boolean relax, final boolean caseSensitive) {
    String key = relax + myPrefix + caseSensitive;
    Matcher pattern = ourPatternCache.get(key);
    if (pattern == null) {
      pattern = createCamelHumpsMatcher(relax, caseSensitive);
      ourPatternCache.put(key, pattern);
    }
    return pattern;
  }


  public boolean prefixMatches(@NotNull final LookupElement element) {
    return prefixMatchersInternal(element, !element.isCaseSensitive());
  }

  private boolean prefixMatchersInternal(final LookupElement element, final boolean itemCaseInsensitive) {
    if (itemCaseInsensitive && myRelaxedMatching) {
      return false;
    }

    for (final String name : element.getAllLookupStrings()) {
      if (itemCaseInsensitive && StringUtil.startsWithIgnoreCase(name, myPrefix) || prefixMatches(name)) {
        return true;
      }
      if (itemCaseInsensitive && CodeInsightSettings.ALL != CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE) {
        if (obtainMatcher(false, false).matches(name)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public PrefixMatcher cloneWithPrefix(@NotNull final String prefix) {
    return new CamelHumpMatcher(prefix, myCaseSensitive, myRelaxedMatching);
  }

  private Matcher createCamelHumpsMatcher(final boolean relaxedMatching, final boolean caseSensitive) {
    if (!caseSensitive) {
      return NameUtil.buildCompletionMatcher(myPrefix, 0, true, true);
    }

    if (relaxedMatching) {
      return NameUtil.buildCompletionMatcher(myPrefix, 0, true, true);
    }

    switch (CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE) {
      case CodeInsightSettings.NONE:
        return NameUtil.buildCompletionMatcher(myPrefix, 0, true, true);
      case CodeInsightSettings.FIRST_LETTER:
        int exactPrefixLen = myPrefix.startsWith("*") ? 0 : 1;
        return NameUtil.buildCompletionMatcher(myPrefix, exactPrefixLen, true, true);
      case CodeInsightSettings.ALL:
        return NameUtil.buildCompletionMatcher(myPrefix, 1, false, false);
      default:
        return NameUtil.buildCompletionMatcher(myPrefix, 0, true, false);
    }
  }

  @Override
  public String toString() {
    return myPrefix;
  }

  public boolean isRelaxedMatching() {
    return myRelaxedMatching;
  }
}
