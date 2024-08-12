// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class FindSymbolParameters {
  private final String myCompletePattern;
  private final String myLocalPatternName;
  private final GlobalSearchScope mySearchScope;
  private final IdFilter myIdFilter;

  /**
   * @deprecated use {@link FindSymbolParameters#FindSymbolParameters(String, String, GlobalSearchScope)} instead.
   * No one should pass `idFilter` explicitly. {@link FileBasedIndex} is responsible to find a proper `idFilter` for provided `scope`.
   */
  @Deprecated
  public FindSymbolParameters(@NotNull String pattern,
                              @NotNull String name,
                              @NotNull GlobalSearchScope scope,
                              @Nullable IdFilter idFilter) {
    myCompletePattern = pattern;
    myLocalPatternName = name;
    mySearchScope = scope;
    myIdFilter = idFilter;
  }

  public FindSymbolParameters(@NotNull String pattern,
                              @NotNull String name,
                              @NotNull GlobalSearchScope scope) {
    this(pattern, name, scope, null);
  }

  public FindSymbolParameters withCompletePattern(@NotNull String pattern) {
    return new FindSymbolParameters(pattern, myLocalPatternName, mySearchScope, myIdFilter);
  }

  public FindSymbolParameters withLocalPattern(@NotNull String pattern) {
    return new FindSymbolParameters(myCompletePattern, pattern, mySearchScope, myIdFilter);
  }

  public FindSymbolParameters withScope(@NotNull GlobalSearchScope scope) {
    return new FindSymbolParameters(myCompletePattern, myLocalPatternName, scope, myIdFilter);
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

  public @NotNull Project getProject() {
    return Objects.requireNonNull(mySearchScope.getProject());
  }

  public boolean isSearchInLibraries() {
    return mySearchScope.isSearchInLibraries();
  }

  public static FindSymbolParameters wrap(@NotNull String pattern, @NotNull Project project, boolean searchInLibraries) {
    return new FindSymbolParameters(pattern, pattern, searchScopeFor(project, searchInLibraries),
                                    ((FileBasedIndexImpl) FileBasedIndex.getInstance()).projectIndexableFiles(project));
  }

  public static FindSymbolParameters wrap(@NotNull String pattern, @NotNull GlobalSearchScope scope) {
    return new FindSymbolParameters(pattern, pattern, scope, null);
  }

  public static FindSymbolParameters simple(@NotNull Project project, boolean searchInLibraries) {
    return new FindSymbolParameters("", "", searchScopeFor(project, searchInLibraries),
                                    ((FileBasedIndexImpl) FileBasedIndex.getInstance()).projectIndexableFiles(project));
  }

  public static @NotNull GlobalSearchScope searchScopeFor(@NotNull Project project, boolean searchInLibraries) {
    return searchInLibraries ? ProjectScope.getAllScope(project) : ProjectScope.getProjectScope(project);
  }
}
