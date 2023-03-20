// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public abstract class PrefixMatcher {
  public static final PrefixMatcher ALWAYS_TRUE = new PlainPrefixMatcher("");
  protected final String myPrefix;

  protected PrefixMatcher(String prefix) {
    myPrefix = prefix;
  }

  public boolean prefixMatches(@NotNull LookupElement element) {
    for (String s : element.getAllLookupStrings()) {
      if (prefixMatches(s)) {
        return true;
      }
    }
    return false;
  }

  public boolean isStartMatch(LookupElement element) {
    for (String s : element.getAllLookupStrings()) {
      if (isStartMatch(s)) {
        return true;
      }
    }
    return false;
  }

  public boolean isStartMatch(String name) {
    return prefixMatches(name);
  }

  public abstract boolean prefixMatches(@NotNull String name);

  @NotNull
  public final String getPrefix() {
    return myPrefix;
  }

  @NotNull public abstract PrefixMatcher cloneWithPrefix(@NotNull String prefix);

  public int matchingDegree(String string) {
    return 0;
  }

  /**
   * Filters _names for strings that match given matcher and sorts them.
   * "Start matching" items go first, then others.
   * Within both groups, names are sorted lexicographically in a case-insensitive way.
   */
  public LinkedHashSet<String> sortMatching(Collection<String> _names) {
    ProgressManager.checkCanceled();
    if (getPrefix().isEmpty()) {
      return new LinkedHashSet<>(_names);
    }

    List<String> sorted = new ArrayList<>();
    for (String name : _names) {
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
}
