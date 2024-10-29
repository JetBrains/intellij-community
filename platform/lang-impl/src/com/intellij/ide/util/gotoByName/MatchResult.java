// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import org.jetbrains.annotations.NotNull;

public final class MatchResult implements Comparable<MatchResult> {
  public final @NotNull String elementName;
  public final int matchingDegree;
  private final boolean startMatch;

  public MatchResult(@NotNull String elementName, int matchingDegree, boolean startMatch) {
    this.elementName = elementName;
    this.matchingDegree = matchingDegree;
    this.startMatch = startMatch;
  }

  public int compareDegrees(@NotNull MatchResult that) {
    return Integer.compare(that.matchingDegree, matchingDegree);
  }

  @Override
  public int compareTo(@NotNull MatchResult that) {
    int result = compareDegrees(that);
    return result != 0 ? result : elementName.compareToIgnoreCase(that.elementName);
  }

  @Override
  public String toString() {
    return "MatchResult{" +
           "'" + elementName + '\'' +
           ", degree=" + matchingDegree +
           ", start=" + startMatch +
           '}';
  }
}
