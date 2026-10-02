// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class FindSymbolParameters {
  /**
   * The {@link #getLimit()} value for a caller that wants every item.
   */
  public static final int UNLIMITED = Integer.MAX_VALUE;

  private final String myCompletePattern;
  private final String myLocalPatternName;
  private final GlobalSearchScope mySearchScope;
  private final IdFilter myIdFilter;
  private final int myLimit;
  private final @Nullable Runnable myCutListener;

  /**
   * @deprecated use {@link FindSymbolParameters#FindSymbolParameters(String, String, GlobalSearchScope)} instead.
   * No one should pass `idFilter` explicitly. {@link FileBasedIndex} is responsible to find a proper `idFilter` for provided `scope`.
   */
  @Deprecated
  public FindSymbolParameters(@NotNull String pattern,
                              @NotNull String name,
                              @NotNull GlobalSearchScope scope,
                              @Nullable IdFilter idFilter) {
    this(pattern, name, scope, idFilter, UNLIMITED, null);
  }

  public FindSymbolParameters(@NotNull String pattern,
                              @NotNull String name,
                              @NotNull GlobalSearchScope scope) {
    this(pattern, name, scope, null, UNLIMITED, null);
  }

  private FindSymbolParameters(@NotNull String pattern,
                               @NotNull String name,
                               @NotNull GlobalSearchScope scope,
                               @Nullable IdFilter idFilter,
                               int limit,
                               @Nullable Runnable cutListener) {
    myCompletePattern = pattern;
    myLocalPatternName = name;
    mySearchScope = scope;
    myIdFilter = idFilter;
    myLimit = limit;
    myCutListener = cutListener;
  }

  public FindSymbolParameters withCompletePattern(@NotNull String pattern) {
    return new FindSymbolParameters(pattern, myLocalPatternName, mySearchScope, myIdFilter, myLimit, myCutListener);
  }

  public FindSymbolParameters withLocalPattern(@NotNull String pattern) {
    return new FindSymbolParameters(myCompletePattern, pattern, mySearchScope, myIdFilter, myLimit, myCutListener);
  }

  public FindSymbolParameters withScope(@NotNull GlobalSearchScope scope) {
    return new FindSymbolParameters(myCompletePattern, myLocalPatternName, scope, myIdFilter, myLimit, myCutListener);
  }

  /**
   * Returns a copy with the given result limit. {@code onCut} runs when a contributor reports a cut, see {@link #reportCut()}.
   * The limit goes only with a cut listener: the filters may thin a cut answer, and only the listener tells the caller to search again.
   */
  @ApiStatus.Internal
  public FindSymbolParameters withLimit(int limit, @NotNull Runnable onCut) {
    return new FindSymbolParameters(myCompletePattern, myLocalPatternName, mySearchScope, myIdFilter, limit, onCut);
  }

  /**
   * A contributor that honors {@link #getLimit()} calls it when it had more than {@code limit} items.
   * The caller may then search again with a bigger limit when the filters thinned the answer.
   * Calling it more than once is fine, from any thread.
   */
  @ApiStatus.Internal
  public void reportCut() {
    if (myCutListener != null) {
      myCutListener.run();
    }
  }

  public @NotNull String getCompletePattern() {
    return myCompletePattern;
  }

  public @NotNull String getLocalPatternName() {
    return myLocalPatternName;
  }

  public @NotNull GlobalSearchScope getSearchScope() {
    return mySearchScope;
  }

  public @Nullable IdFilter getIdFilter() {
    return myIdFilter;
  }

  /**
   * The caller takes at most this many items from this call.
   * <p>
   * A contributor that can rank its items should produce its best {@code limit} items, plus one more when it has more,
   * so the caller can show "more". Producing more is allowed, just wasted.
   * A contributor that produces its best {@code limit + 1} items also calls {@link #reportCut()}.
   * {@link #UNLIMITED} means the caller wants every item.
   */
  public int getLimit() {
    return myLimit;
  }

  public @NotNull Project getProject() {
    return Objects.requireNonNull(mySearchScope.getProject());
  }

  public boolean isSearchInLibraries() {
    return mySearchScope.isSearchInLibraries();
  }

  public static FindSymbolParameters wrap(@NotNull String pattern, @NotNull Project project, boolean searchInLibraries) {
    return new FindSymbolParameters(pattern, pattern, searchScopeFor(project, searchInLibraries),
                                    FileBasedIndex.getInstance().projectIndexableFiles(project));
  }

  public static FindSymbolParameters wrap(@NotNull String pattern, @NotNull GlobalSearchScope scope) {
    return new FindSymbolParameters(pattern, pattern, scope, null);
  }

  public static FindSymbolParameters simple(@NotNull Project project, boolean searchInLibraries) {
    return new FindSymbolParameters("", "", searchScopeFor(project, searchInLibraries),
                                    FileBasedIndex.getInstance().projectIndexableFiles(project));
  }

  public static @NotNull GlobalSearchScope searchScopeFor(@NotNull Project project, boolean searchInLibraries) {
    return searchInLibraries ? ProjectScope.getAllScope(project) : ProjectScope.getProjectScope(project);
  }
}
