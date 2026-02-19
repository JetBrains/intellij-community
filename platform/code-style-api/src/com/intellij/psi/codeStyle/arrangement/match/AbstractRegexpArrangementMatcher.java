// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public abstract class AbstractRegexpArrangementMatcher implements ArrangementEntryMatcher {
  
  private final @NotNull String myPattern;

  private final @Nullable Pattern myCompiledPattern;

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
  
  protected abstract @Nullable String getTextToMatch(@NotNull ArrangementEntry entry);

  public @NotNull String getPattern() {
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
