
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 *
 */
public class FindUsagesOptions extends UserDataHolderBase implements Cloneable {
  public SearchScope searchScope;

  public boolean isSearchForTextOccurrences = true;

  public boolean isUsages = false;
  public SearchRequestCollector fastTrack = null;

  public FindUsagesOptions(@NotNull Project project, @Nullable final DataContext dataContext) {
    String defaultScopeName = FindSettings.getInstance().getDefaultScopeName();
    List<SearchScope> predefined = ScopeChooserCombo.getPredefinedScopes(project, dataContext, true, false, false, false);
    for (SearchScope scope : predefined) {
      if (scope.getDisplayName().equals(defaultScopeName)) {
        searchScope = scope;
        break;
      }
    }
    if (searchScope == null) {
      searchScope = ProjectScope.getProjectScope(project);
    }
  }

  public FindUsagesOptions(SearchScope searchScope) {
    this.searchScope = searchScope;
  }

  public Object clone() {
    return super.clone();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FindUsagesOptions that = (FindUsagesOptions)o;

    if (isSearchForTextOccurrences != that.isSearchForTextOccurrences) return false;
    if (isUsages != that.isUsages) return false;
    if (searchScope != null ? !searchScope.equals(that.searchScope) : that.searchScope != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (searchScope != null ? searchScope.hashCode() : 0);
    result = 31 * result + (isSearchForTextOccurrences ? 1 : 0);
    result = 31 * result + (isUsages ? 1 : 0);
    return result;
  }

  public String generateUsagesString() {
    return FindBundle.message("find.usages.panel.title.usages");
  }
}
