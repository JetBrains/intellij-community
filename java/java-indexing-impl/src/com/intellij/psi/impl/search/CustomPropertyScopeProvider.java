// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

public interface CustomPropertyScopeProvider {

  ExtensionPointName<CustomPropertyScopeProvider> EP_NAME = new ExtensionPointName<>("com.intellij.customPropertyScopeProvider");

  @NotNull
  SearchScope getScope(@NotNull Project project);

  @NotNull
  static SearchScope getPropertyScope(@NotNull Project project) {
    SearchScope additional = GlobalSearchScope.EMPTY_SCOPE;
    for (CustomPropertyScopeProvider provider : EP_NAME.getExtensions()) {
      additional = additional.union(provider.getScope(project));
    }
    return additional;
  }
}
