// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SearchWordRequest {

  final @NotNull String word;
  final @NotNull SearchScope searchScope;
  final boolean caseSensitive;
  final short searchContext;
  final @Nullable String containerName;

  SearchWordRequest(@NotNull String word,
                    @NotNull SearchScope searchScope,
                    boolean caseSensitive,
                    short searchContext,
                    @Nullable String containerName) {
    this.word = word;
    this.searchScope = searchScope;
    this.caseSensitive = caseSensitive;
    this.searchContext = searchContext;
    this.containerName = containerName;
  }

  boolean shouldProcessInjectedPsi() {
    return !(searchScope instanceof LocalSearchScope) || !((LocalSearchScope)searchScope).isIgnoreInjectedPsi();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SearchWordRequest request = (SearchWordRequest)o;

    if (!word.equals(request.word)) return false;
    if (caseSensitive != request.caseSensitive) return false;
    if (searchContext != request.searchContext) return false;
    if (containerName != null ? !containerName.equals(request.containerName) : request.containerName != null) return false;
    if (!searchScope.equals(request.searchScope)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = word.hashCode();
    result = 31 * result + searchScope.hashCode();
    result = 31 * result + (caseSensitive ? 1 : 0);
    result = 31 * result + (int)searchContext;
    result = 31 * result + (containerName != null ? containerName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "SearchWordRequest{" +
           "word='" + word + '\'' +
           ", searchScope=" + searchScope +
           ", caseSensitive=" + caseSensitive +
           ", searchContext=" + searchContext +
           ", containerName='" + containerName + "'}";
  }
}
