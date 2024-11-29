// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find.findUsages;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.find.FindUsagesSettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.PredefinedSearchScopeProvider;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.*;

import java.util.List;

public class FindUsagesOptions implements Cloneable {
  public @NotNull SearchScope searchScope;
  @ApiStatus.Internal
  public boolean isMaximalScope = false;

  public boolean isSearchForTextOccurrences = true;

  public boolean isUsages;
  public SearchRequestCollector fastTrack;

  public FindUsagesOptions(@NotNull Project project) {
    this(project, null);
  }

  public FindUsagesOptions(@NotNull Project project, final @Nullable DataContext dataContext) {
    this(findScopeByName(project, dataContext, FindUsagesSettings.getInstance().getDefaultScopeName()));
  }

  public FindUsagesOptions(@NotNull SearchScope searchScope) {
    this.searchScope = searchScope;
  }

  @ApiStatus.Internal
  public static @NotNull SearchScope findScopeByName(@NotNull Project project,
                                                     @Nullable DataContext dataContext,
                                                     @Nullable String scopeName) {
    List<? extends SearchScope> predefined = PredefinedSearchScopeProvider.getInstance().getPredefinedScopes(
      project, dataContext, true, false, false, false, false);
    for (SearchScope scope : predefined) {
      if (scope.getDisplayName().equals(scopeName)) {
        return scope;
      }
    }
    return getDefaultScope(project);
  }

  @ApiStatus.Internal
  public static @NotNull SearchScope getDefaultScope(@NotNull Project project) {
    return ProjectScope.getProjectScope(project);
  }

  @Override
  public FindUsagesOptions clone() {
    try {
      return (FindUsagesOptions)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FindUsagesOptions that = (FindUsagesOptions)o;

    if (isSearchForTextOccurrences != that.isSearchForTextOccurrences) return false;
    if (isUsages != that.isUsages) return false;
    return searchScope.equals(that.searchScope);
  }

  @Override
  public int hashCode() {
    int result = searchScope.hashCode();
    result = 31 * result + (isSearchForTextOccurrences ? 1 : 0);
    result = 31 * result + (isUsages ? 1 : 0);
    return result;
  }

  @Override
  public @NonNls String toString() {
    return getClass().getSimpleName() + "{" +
           "searchScope=" + searchScope +
           ", isSearchForTextOccurrences=" + isSearchForTextOccurrences +
           ", isUsages=" + isUsages +
           '}';
  }

  public @NotNull @Nls String generateUsagesString() {
    return AnalysisBundle.message("find.usages.panel.title.usages");
  }
}
