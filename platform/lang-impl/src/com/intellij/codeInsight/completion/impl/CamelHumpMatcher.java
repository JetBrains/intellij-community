
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class CamelHumpMatcher extends PrefixMatcher {
  private final Matcher myMatcher;
  private final boolean myCaseSensitive;

  public CamelHumpMatcher(@NotNull final String prefix) {
    this(prefix, true);
  }

  public CamelHumpMatcher(String prefix, boolean caseSensitive) {
    super(prefix);
    myCaseSensitive = caseSensitive;
    myMatcher = createMatcher(myCaseSensitive);
  }

  public boolean prefixMatches(@NotNull final String name) {
    return myMatcher.matches(name);
  }

  public boolean prefixMatches(@NotNull final LookupElement element) {
    return prefixMatchersInternal(element, !element.isCaseSensitive());
  }

  private boolean prefixMatchersInternal(final LookupElement element, final boolean itemCaseInsensitive) {
    for (final String name : element.getAllLookupStrings()) {
      if (itemCaseInsensitive && StringUtil.startsWithIgnoreCase(name, myPrefix) || prefixMatches(name)) {
        return true;
      }
      if (itemCaseInsensitive && CodeInsightSettings.ALL != CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE) {
        if (createMatcher(false).matches(name)) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public PrefixMatcher cloneWithPrefix(@NotNull final String prefix) {
    return new CamelHumpMatcher(prefix, myCaseSensitive);
  }

  private Matcher createMatcher(final boolean caseSensitive) {
    String prefix = applyMiddleMatching(myPrefix);

    if (!caseSensitive) {
      return NameUtil.buildCompletionMatcher(prefix, 0, true, true);
    }

    switch (CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE) {
      case CodeInsightSettings.NONE:
        return NameUtil.buildCompletionMatcher(prefix, 0, true, true);
      case CodeInsightSettings.FIRST_LETTER:
        int exactPrefixLen = prefix.startsWith("*") ? 0 : 1;
        return NameUtil.buildCompletionMatcher(prefix, exactPrefixLen, true, true);
      case CodeInsightSettings.ALL:
        return NameUtil.buildCompletionMatcher(prefix, 1, false, false);
      default:
        return NameUtil.buildCompletionMatcher(prefix, 0, true, false);
    }
  }

  public static String applyMiddleMatching(String prefix) {
    if (Registry.is("ide.completion.middle.matching") && !prefix.isEmpty() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return " " + StringUtil.replace(prefix, ".", ". ").trim();
    }
    return prefix;
  }

  @Override
  public String toString() {
    return myPrefix;
  }

}
