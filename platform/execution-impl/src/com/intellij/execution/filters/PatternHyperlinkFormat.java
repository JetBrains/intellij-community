/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.filters;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

public final class PatternHyperlinkFormat {
  private final Pattern myPattern;
  private final boolean myZeroBasedLineNumbering;
  private final boolean myZeroBasedColumnNumbering;
  private final PatternHyperlinkPart[] myLinkParts;
  private final List<String> myRequiredOrderedSubstrings;

  public PatternHyperlinkFormat(@NotNull Pattern pattern,
                                boolean zeroBasedLineNumbering,
                                boolean zeroBasedColumnNumbering,
                                PatternHyperlinkPart @NotNull ... linkParts) {
    this(pattern, zeroBasedLineNumbering, zeroBasedColumnNumbering, List.of(), linkParts);
  }

  public PatternHyperlinkFormat(@NotNull Pattern pattern,
                                boolean zeroBasedLineNumbering,
                                boolean zeroBasedColumnNumbering,
                                @NotNull List<String> requiredOrderedSubstrings,
                                PatternHyperlinkPart @NotNull ... linkParts) {
    myPattern = pattern;
    myZeroBasedLineNumbering = zeroBasedLineNumbering;
    myZeroBasedColumnNumbering = zeroBasedColumnNumbering;
    myRequiredOrderedSubstrings = List.copyOf(requiredOrderedSubstrings);
    myLinkParts = linkParts;
  }

  @NotNull Pattern getPattern() {
    return myPattern;
  }

  boolean isZeroBasedLineNumbering() {
    return myZeroBasedLineNumbering;
  }

  boolean isZeroBasedColumnNumbering() {
    return myZeroBasedColumnNumbering;
  }

  PatternHyperlinkPart @NotNull [] getLinkParts() {
    return myLinkParts;
  }

  boolean matchRequiredSubstrings(@NotNull String line) {
    int ind = 0;
    for (String required : myRequiredOrderedSubstrings) {
      int nextInd = line.indexOf(required, ind);
      if (nextInd < 0) {
        return false;
      }
      ind = nextInd + required.length();
    }
    return true;
  }
}
