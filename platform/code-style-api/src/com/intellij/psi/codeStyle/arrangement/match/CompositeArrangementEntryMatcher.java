// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CompositeArrangementEntryMatcher implements ArrangementEntryMatcher {

  private final @NotNull Set<ArrangementEntryMatcher> myMatchers = new HashSet<>();

  public CompositeArrangementEntryMatcher(ArrangementEntryMatcher @NotNull ... matchers) {
    myMatchers.addAll(Arrays.asList(matchers));
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    for (ArrangementEntryMatcher matcher : myMatchers) {
      if (!matcher.isMatched(entry)) {
        return false;
      }
    }
    return true;
  }

  public void addMatcher(@NotNull ArrangementEntryMatcher rule) {
    myMatchers.add(rule);
  }

  @Override
  public int hashCode() {
    return myMatchers.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CompositeArrangementEntryMatcher matcher = (CompositeArrangementEntryMatcher)o;

    return myMatchers.equals(matcher.myMatchers);
  }

  @Override
  public String toString() {
    return String.format("all of those: %s", myMatchers);
  }
}
