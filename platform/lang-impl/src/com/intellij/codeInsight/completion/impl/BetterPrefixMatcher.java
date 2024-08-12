// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BetterPrefixMatcher extends PrefixMatcher {
  private final PrefixMatcher myOriginal;
  private final @Nullable CamelHumpMatcher myHumpMatcher;
  private final int myMinMatchingDegree;

  public BetterPrefixMatcher(PrefixMatcher original, int minMatchingDegree) {
    super(original.getPrefix());
    myOriginal = original;
    myHumpMatcher = original instanceof CamelHumpMatcher ? (CamelHumpMatcher)original : null;
    myMinMatchingDegree = minMatchingDegree;
  }

  public @NotNull BetterPrefixMatcher improve(CompletionResult result) {
    int degree = RealPrefixMatchingWeigher.getBestMatchingDegree(result.getLookupElement(), result.getPrefixMatcher());
    if (degree <= myMinMatchingDegree) return this;

    return createCopy(myOriginal, degree);
  }

  protected @NotNull BetterPrefixMatcher createCopy(PrefixMatcher original, int degree) {
    return new BetterPrefixMatcher(original, degree);
  }

  @Override
  public boolean prefixMatches(@NotNull String name) {
    return prefixMatchesEx(name) == MatchingOutcome.BETTER_MATCH;
  }

  protected MatchingOutcome prefixMatchesEx(String name) {
    return myHumpMatcher != null ? matchOptimized(name, myHumpMatcher) : matchGeneric(name);
  }

  private MatchingOutcome matchGeneric(String name) {
    if (!myOriginal.prefixMatches(name)) return MatchingOutcome.NON_MATCH;
    if (!myOriginal.isStartMatch(name)) return MatchingOutcome.WORSE_MATCH;
    return myOriginal.matchingDegree(name) >= myMinMatchingDegree ? MatchingOutcome.BETTER_MATCH : MatchingOutcome.WORSE_MATCH;
  }

  private MatchingOutcome matchOptimized(String name, CamelHumpMatcher matcher) {
    FList<TextRange> fragments = matcher.matchingFragments(name);
    if (fragments == null) return MatchingOutcome.NON_MATCH;
    if (!MinusculeMatcher.isStartMatch(fragments)) return MatchingOutcome.WORSE_MATCH;
    return matcher.matchingDegree(name, fragments) >= myMinMatchingDegree ? MatchingOutcome.BETTER_MATCH : MatchingOutcome.WORSE_MATCH;
  }

  protected enum MatchingOutcome {
    NON_MATCH, WORSE_MATCH, BETTER_MATCH
  }

  @Override
  public boolean isStartMatch(String name) {
    return myOriginal.isStartMatch(name);
  }

  @Override
  public int matchingDegree(String string) {
    return myOriginal.matchingDegree(string);
  }

  @Override
  public @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
    return createCopy(myOriginal.cloneWithPrefix(prefix), myMinMatchingDegree);
  }

  public static final class AutoRestarting extends BetterPrefixMatcher {
    private final CompletionResultSet myResult;

    public AutoRestarting(@NotNull CompletionResultSet result) {
      this(result, result.getPrefixMatcher(), Integer.MIN_VALUE);
    }

    private AutoRestarting(CompletionResultSet result, PrefixMatcher original, int minMatchingDegree) {
      super(original, minMatchingDegree);
      myResult = result;
    }

    @Override
    protected @NotNull BetterPrefixMatcher createCopy(PrefixMatcher original, int degree) {
      return new AutoRestarting(myResult, original, degree);
    }

    @Override
    protected MatchingOutcome prefixMatchesEx(String name) {
      MatchingOutcome outcome = super.prefixMatchesEx(name);
      if (outcome == MatchingOutcome.WORSE_MATCH) {
        myResult.restartCompletionOnAnyPrefixChange();
      }
      return outcome;
    }
  }
}
