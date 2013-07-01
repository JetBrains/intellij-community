
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author peter
*/
public class CamelHumpMatcher extends PrefixMatcher {
  private final MinusculeMatcher myMatcher;
  private final MinusculeMatcher myCaseInsensitiveMatcher;
  private final boolean myCaseSensitive;
  private static boolean ourForceStartMatching;


  public CamelHumpMatcher(@NotNull final String prefix) {
    this(prefix, true);
  }

  public CamelHumpMatcher(String prefix, boolean caseSensitive) {
    super(prefix);
    myCaseSensitive = caseSensitive;
    myMatcher = createMatcher(myCaseSensitive);
    myCaseInsensitiveMatcher = createMatcher(false);
  }

  @Override
  public boolean isStartMatch(String name) {
    return myMatcher.isStartMatch(name);
  }

  @Override
  public boolean isStartMatch(LookupElement element) {
    for (String s : element.getAllLookupStrings()) {
      FList<TextRange> ranges = myCaseInsensitiveMatcher.matchingFragments(s);
      if (ranges == null) continue;
      if (ranges.isEmpty() || isStartMatchModuloUnderscores(s, ranges.get(0).getStartOffset())) {
        return true;
      }
    }

    return false;
  }

  private static boolean isStartMatchModuloUnderscores(@NotNull String name, int startIndex) {
    for (int i = 0; i < startIndex; i++) {
      if (name.charAt(i) != '_') {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean prefixMatches(@NotNull final String name) {
    return myMatcher.matches(name);
  }

  @Override
  public boolean prefixMatches(@NotNull final LookupElement element) {
    return prefixMatchersInternal(element, !element.isCaseSensitive());
  }

  private boolean prefixMatchersInternal(final LookupElement element, final boolean itemCaseInsensitive) {
    for (final String name : element.getAllLookupStrings()) {
      if (itemCaseInsensitive && StringUtil.startsWithIgnoreCase(name, myPrefix) || prefixMatches(name)) {
        return true;
      }
      if (itemCaseInsensitive && CodeInsightSettings.ALL != CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE) {
        if (myCaseInsensitiveMatcher.matches(name)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  @NotNull
  public PrefixMatcher cloneWithPrefix(@NotNull final String prefix) {
    return new CamelHumpMatcher(prefix, myCaseSensitive);
  }

  private MinusculeMatcher createMatcher(final boolean caseSensitive) {
    String prefix = applyMiddleMatching(myPrefix);

    if (!caseSensitive) {
      return NameUtil.buildMatcher(prefix, NameUtil.MatchingCaseSensitivity.NONE);
    }

    switch (CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE) {
      case CodeInsightSettings.NONE:
        return NameUtil.buildMatcher(prefix, NameUtil.MatchingCaseSensitivity.NONE);
      case CodeInsightSettings.FIRST_LETTER:
        return NameUtil.buildMatcher(prefix, NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
      default:
        return NameUtil.buildMatcher(prefix, NameUtil.MatchingCaseSensitivity.ALL);
    }
  }

  public static String applyMiddleMatching(String prefix) {
    if (Registry.is("ide.completion.middle.matching") && !prefix.isEmpty() && !ourForceStartMatching) {
      return "*" + StringUtil.replace(prefix, ".", ". ").trim();
    }
    return prefix;
  }

  @Override
  public String toString() {
    return myPrefix;
  }

  /**
   * In an ideal world, all tests would use the same settings as production, i.e. middle matching.
   * If you see a usage of this method which can be easily removed (i.e. it's easy to make a test pass without it
   * by modifying test expectations slightly), please do it
   */
  @TestOnly
  @Deprecated
  public static void forceStartMatching(Disposable parent) {
    ourForceStartMatching = true;
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourForceStartMatching = false;
      }
    });
  }

  @Override
  public int matchingDegree(String string) {
    return myMatcher.matchingDegree(string);
  }
}
