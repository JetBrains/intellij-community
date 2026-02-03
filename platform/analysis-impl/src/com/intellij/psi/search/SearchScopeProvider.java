// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface SearchScopeProvider {
  ExtensionPointName<SearchScopeProvider> EP_NAME = ExtensionPointName.create("com.intellij.searchScopesProvider");

  default @Nullable @NlsContexts.Separator String getDisplayName() {
    return null;
  }

  default @NotNull List<SearchScope> getSearchScopes(@NotNull Project project, @NotNull DataContext dataContext) {
    return Collections.emptyList();
  }

  /**
   * General project scopes are added after 'Project', 'Everything' but before 'Production', 'Tests', etc.
   *
   * @see PredefinedSearchScopeProvider
   */
  default List<SearchScope> getGeneralSearchScopes(@NotNull Project project, @NotNull DataContext dataContext) {
    return Collections.emptyList();
  }
}
