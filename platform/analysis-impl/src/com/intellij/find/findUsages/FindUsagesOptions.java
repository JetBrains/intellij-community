/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.find.findUsages;

import com.intellij.find.FindSettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.PredefinedSearchScopeProvider;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FindUsagesOptions implements Cloneable {
  @NotNull
  public SearchScope searchScope;

  public boolean isSearchForTextOccurrences = true;

  public boolean isUsages;
  public SearchRequestCollector fastTrack;

  public FindUsagesOptions(@NotNull Project project) {
    this(project, null);
  }

  public FindUsagesOptions(@NotNull Project project, @Nullable final DataContext dataContext) {
    this(findScopeByName(project, dataContext, FindSettings.getInstance().getDefaultScopeName()));
  }

  public FindUsagesOptions(@NotNull SearchScope searchScope) {
    this.searchScope = searchScope;
  }

  @NotNull
  private static SearchScope findScopeByName(@NotNull Project project,
                                             @Nullable DataContext dataContext,
                                             @Nullable String scopeName) {
    List<SearchScope> predefined = PredefinedSearchScopeProvider.getInstance().getPredefinedScopes(
      project, dataContext, true, false, false, false, false);
    for (SearchScope scope : predefined) {
      if (scope.getDisplayName().equals(scopeName)) {
        return scope;
      }
    }
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

  @NonNls
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
           "searchScope=" + searchScope +
           ", isSearchForTextOccurrences=" + isSearchForTextOccurrences +
           ", isUsages=" + isUsages +
           '}';
  }

  @NotNull
  public String generateUsagesString() {
    return "Usages";
  }
}
