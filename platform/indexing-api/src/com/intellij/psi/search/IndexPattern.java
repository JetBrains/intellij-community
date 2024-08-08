// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A single pattern, the occurrences of which in comments are indexed by the IDE.
 *
 * @see IndexPatternProvider#getIndexPatterns()
 */
public final class IndexPattern {
  public static final IndexPattern[] EMPTY_ARRAY = new IndexPattern[0];

  private @NotNull String myPatternString;
  private Pattern myOptimizedIndexingPattern;
  private boolean myCaseSensitive;
  private Pattern myPattern;
  private @NotNull List<String> myStringsToFindFirst = Collections.emptyList();

  /**
   * Creates an instance of an index pattern.
   *
   * @param patternString the text of the Java regular expression to match.
   * @param caseSensitive whether the regular expression should be case-sensitive.
   */
  public IndexPattern(@NotNull String patternString, boolean caseSensitive) {
    myPatternString = patternString;
    myCaseSensitive = caseSensitive;
    compilePattern();
  }

  public @NotNull @NlsSafe String getPatternString() {
    return myPatternString;
  }

  public @Nullable Pattern getPattern() {
    return myPattern;
  }

  public @Nullable Pattern getOptimizedIndexingPattern() {
    return myOptimizedIndexingPattern;
  }

  public @NotNull List<String> getWordsToFindFirst() {
    return myStringsToFindFirst;
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public void setPatternString(@NotNull String patternString) {
    myPatternString = patternString;
    compilePattern();
  }

  public void setCaseSensitive(boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
    compilePattern();
  }

  private void compilePattern() {
    try {
      int flags = 0;
      if (!myCaseSensitive) {
        flags = Pattern.CASE_INSENSITIVE;
        if (StringUtil.findFirst(myPatternString, c -> c >= 0x80) >= 0) {
          flags |= Pattern.UNICODE_CASE;
        }
      }
      myPattern = Pattern.compile(myPatternString, flags);
      var optimizedPattern = myPatternString;
      optimizedPattern = StringUtil.trimStart(optimizedPattern, ".*");
      myOptimizedIndexingPattern = Pattern.compile(optimizedPattern, flags);
      myStringsToFindFirst = IndexPatternOptimizer.getInstance().extractStringsToFind(myPatternString);
    }
    catch(PatternSyntaxException e){
      myPattern = null;
      myOptimizedIndexingPattern = null;
      myStringsToFindFirst = Collections.emptyList();
    }
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof IndexPattern ip && myCaseSensitive == ip.myCaseSensitive && myPatternString.equals(ip.myPatternString);
  }

  @Override
  public int hashCode() {
    return 29 * myPatternString.hashCode() + (myCaseSensitive ? 1 : 0);
  }
}
