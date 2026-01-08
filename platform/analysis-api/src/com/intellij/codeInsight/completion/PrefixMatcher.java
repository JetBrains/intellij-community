// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.text.matching.MatchedFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Base class for prefix-based matching logic used in code completion.
 * Determines whether strings or lookup elements match a given prefix
 * and provides utilities for sorting and highlighting matches.
 *
 * @see PlainPrefixMatcher#ALWAYS_TRUE
 */
public abstract class PrefixMatcher {
  /**
   * @deprecated Use {@link PlainPrefixMatcher#ALWAYS_TRUE} instead.
   */
  @Deprecated
  public static final PrefixMatcher ALWAYS_TRUE = new PlainPrefixMatcher("");

  protected final String myPrefix;

  protected PrefixMatcher(@NotNull String prefix) {
    myPrefix = prefix;
  }

  /**
   * @return true if {@code element} matches this prefix matcher, false otherwise.
   */
  public boolean prefixMatches(@NotNull LookupElement element) {
    for (String s : element.getAllLookupStrings()) {
      if (prefixMatches(s)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return true if {@code element} matches this prefix matcher and the matched substring is at the start of the lookup string.
   */
  public boolean isStartMatch(@NotNull LookupElement element) {
    for (String s : element.getAllLookupStrings()) {
      if (isStartMatch(s)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return true if {@code name} matches this prefix matcher and the matched substring is at the start of the lookup string.
   */
  public boolean isStartMatch(@NotNull String name) {
    return prefixMatches(name);
  }

  /**
   * @return true if {@code name} matches this prefix matcher.
   */
  public abstract boolean prefixMatches(@NotNull String name);

  /**
   * @return the prefix used for matching.
   */
  public final @NotNull String getPrefix() {
    return myPrefix;
  }

  /**
   * @return the same-logic prefix matcher but using {@code prefix} for matching.
   */
  public abstract @NotNull PrefixMatcher cloneWithPrefix(@NotNull String prefix);

  /**
   * @return matching degree of the given name. The bigger result, the better match.
   * <p>
   * Used by sorting algorithms to show better matches higher in the completion list.
   */
  public int matchingDegree(@NotNull String name) {
    return 0;
  }

  /**
   * Filters {@code names} for strings that match given matcher and sorts them.
   * "Start matching" items go first, then others.
   * Within both groups, names are sorted lexicographically in a case-insensitive way.
   * <p>
   * IntelliJ completion machinery ignores new results after a certain limit is reached, see {@code ide.completion.variant.limit} registry key.
   * Thus, if your {@link CompletionContributor} wants to provide a potentially large number of items, it should pass more relevant ones first.
   * Use {@code sortMatching} to sort them in the desired order.
   */
  public @NotNull LinkedHashSet<String> sortMatching(@NotNull Collection<@NotNull String> names) {
    ProgressManager.checkCanceled();
    if (getPrefix().isEmpty()) {
      return new LinkedHashSet<>(names);
    }

    List<String> sorted = new ArrayList<>();
    for (String name : names) {
      if (prefixMatches(name)) {
        sorted.add(name);
      }
    }

    ProgressManager.checkCanceled();
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    ProgressManager.checkCanceled();

    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String name : sorted) {
      if (isStartMatch(name)) {
        result.add(name);
      }
    }

    ProgressManager.checkCanceled();

    result.addAll(sorted);
    return result;
  }

  /**
   * @return a list of text ranges in the given name that match the prefix.
   */
  public @Nullable List<@NotNull MatchedFragment> getMatchingFragments(@NotNull String name) {
    return null;
  }
}
