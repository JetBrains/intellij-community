/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A single pattern the occurrences of which in comments are indexed by IDEA.
 *
 * @author yole
 * @since 5.1
 * @see IndexPatternProvider#getIndexPatterns()
 */
public class IndexPattern {
  @NotNull private String myPatternString;
  private Pattern myOptimizedIndexingPattern;
  private boolean myCaseSensitive;
  private Pattern myPattern;

  /**
   * Creates an instance of an index pattern.
   *
   * @param patternString the text of the Java regular expression to match.
   * @param caseSensitive whether the regular expression should be case-sensitive.
   */
  public IndexPattern(@NotNull String patternString, final boolean caseSensitive) {
    myPatternString = patternString;
    myCaseSensitive = caseSensitive;
    compilePattern();
  }

  @NotNull
  public String getPatternString() {
    return myPatternString;
  }

  public @Nullable Pattern getPattern() {
    return myPattern;
  }

  public @Nullable Pattern getOptimizedIndexingPattern() {
    return myOptimizedIndexingPattern;
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public void setPatternString(@NotNull final String patternString) {
    myPatternString = patternString;
    compilePattern();
  }

  public void setCaseSensitive(final boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
    compilePattern();
  }

  private void compilePattern() {
    try {
      int flags = 0;
      if (!myCaseSensitive) {
        flags = Pattern.CASE_INSENSITIVE;
      }
      myPattern = Pattern.compile(myPatternString, flags);
      String optimizedPattern = myPatternString;
      optimizedPattern = StringUtil.trimStart(optimizedPattern, ".*");
      myOptimizedIndexingPattern = Pattern.compile(optimizedPattern, flags);
    }
    catch(PatternSyntaxException e){
      myPattern = null;
      myOptimizedIndexingPattern = null;
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IndexPattern that = (IndexPattern)o;

    if (myCaseSensitive != that.myCaseSensitive) return false;
    if (!myPatternString.equals(that.myPatternString)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPatternString.hashCode();
    result = 29 * result + (myCaseSensitive ? 1 : 0);
    return result;
  }
}
