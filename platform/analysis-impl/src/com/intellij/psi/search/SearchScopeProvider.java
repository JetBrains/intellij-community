// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.List;

public interface SearchScopeProvider {
  ExtensionPointName<SearchScopeProvider> EP = ExtensionPointName.create("com.intellij.searchScopesProvider");

  /**
   * General project scopes are added after 'Project', 'Everything' but before 'Production', 'Tests', etc.
   * @see PredefinedSearchScopeProvider
   */
  List<SearchScope> getGeneralProjectScopes();
}
