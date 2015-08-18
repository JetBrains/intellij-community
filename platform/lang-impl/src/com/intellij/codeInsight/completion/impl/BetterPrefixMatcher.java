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
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class BetterPrefixMatcher extends PrefixMatcher {
  private final PrefixMatcher myOriginal;
  @Nullable private final CamelHumpMatcher myHumpMatcher;
  private final int myMinMatchingDegree;

  public BetterPrefixMatcher(PrefixMatcher original, int minMatchingDegree) {
    super(original.getPrefix());
    myOriginal = original;
    myHumpMatcher = original instanceof CamelHumpMatcher ? (CamelHumpMatcher)original : null;
    myMinMatchingDegree = minMatchingDegree;
  }

  public BetterPrefixMatcher(CompletionResultSet set) {
    this(set.getPrefixMatcher(), Integer.MIN_VALUE);
  }

  @NotNull
  public BetterPrefixMatcher improve(CompletionResult result) {
    int degree = RealPrefixMatchingWeigher.getBestMatchingDegree(result.getLookupElement(), result.getPrefixMatcher());
    if (degree <= myMinMatchingDegree) return this;

    return new BetterPrefixMatcher(myOriginal, degree);
  }
  
  @Override
  public boolean prefixMatches(@NotNull String name) {
    if (myHumpMatcher != null) {
      FList<TextRange> fragments = myHumpMatcher.matchingFragments(name);
      if (fragments == null || !MinusculeMatcher.isStartMatch(fragments)) {
        return false;
      }
      return myHumpMatcher.matchingDegree(name, fragments) >= myMinMatchingDegree;
    }

    if (!myOriginal.prefixMatches(name) || !myOriginal.isStartMatch(name)) {
      return false;
    }
    return myOriginal.matchingDegree(name) >= myMinMatchingDegree;
  }

  @Override
  public boolean isStartMatch(String name) {
    return myOriginal.isStartMatch(name);
  }

  @Override
  public int matchingDegree(String string) {
    return myOriginal.matchingDegree(string);
  }

  @NotNull
  @Override
  public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
    return new BetterPrefixMatcher(myOriginal.cloneWithPrefix(prefix), myMinMatchingDegree);
  }
}
