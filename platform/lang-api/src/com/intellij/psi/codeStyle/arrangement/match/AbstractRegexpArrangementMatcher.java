/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 1:02 PM
 */
public abstract class AbstractRegexpArrangementMatcher implements ArrangementEntryMatcher {
  
  @NotNull private final String myPattern;

  @Nullable private final Pattern myCompiledPattern;

  public AbstractRegexpArrangementMatcher(@NotNull String pattern) {
    myPattern = pattern;
    Pattern p = null;
    try {
      p = Pattern.compile(pattern);
    }
    catch (Exception e) {
      // ignore
    }
    myCompiledPattern = p;
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    if (myCompiledPattern == null) {
      return false;
    }
    String text = getTextToMatch(entry);
    return text != null && myCompiledPattern.matcher(text).matches();
  }
  
  @Nullable
  protected abstract String getTextToMatch(@NotNull ArrangementEntry entry);

  @NotNull
  public String getPattern() {
    return myPattern;
  }

  @Override
  public int hashCode() {
    return myPattern.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractRegexpArrangementMatcher that = (AbstractRegexpArrangementMatcher)o;
    return myPattern.equals(that.myPattern);
  }

  @Override
  public String toString() {
    return String.format("regexp '%s'", myPattern);
  }
}
