/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A single pattern the occurrences of which in comments are indexed by IDEA.
 *
 * @author yole
 * @see IndexPatternProvider#getIndexPatterns()
 */
public class IndexPattern {
  /**
   * Pattern for "todo" matching. It cannot be null.
   */
  private String myPatternString;
  private boolean myCaseSensitive;
  private Pattern myPattern;

  public IndexPattern(@NotNull String patternString, final boolean caseSensitive) {
    myPatternString = patternString;
    myCaseSensitive = caseSensitive;
    compilePattern();
  }

  public String getPatternString() {
    return myPatternString;
  }

  public Pattern getPattern() {
    return myPattern;
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public void setPatternString(final String patternString) {
    myPatternString = patternString;
    compilePattern();
  }

  public void setCaseSensitive(final boolean caseSensitive) {
    myCaseSensitive = caseSensitive;
    compilePattern();
  }

  private void compilePattern() {
    try{
      if (myCaseSensitive){
        myPattern = Pattern.compile(myPatternString);
      }
      else{
        myPattern = Pattern.compile(myPatternString, Pattern.CASE_INSENSITIVE);
      }
    }
    catch(PatternSyntaxException e){
      myPattern = null;
    }
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IndexPattern that = (IndexPattern)o;

    if (myCaseSensitive != that.myCaseSensitive) return false;
    if (!myPatternString.equals(that.myPatternString)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myPatternString.hashCode();
    result = 29 * result + (myCaseSensitive ? 1 : 0);
    return result;
  }
}
