// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class CustomScopesAggregator {
  public static @NotNull List<NamedScope> getAllCustomScopes(@NotNull Project project) {
    Set<NamedScope> allScopes = new LinkedHashSet<>();
    for (CustomScopesProvider scopesProvider : CustomScopesProvider.CUSTOM_SCOPES_PROVIDER.getExtensions(project)) {
      List<NamedScope> customScopes = scopesProvider.getFilteredScopes();
      if (customScopes.contains(null)) {
        throw PluginException.createByClass("CustomScopesProvider::getFilteredScopes() must not return null scopes, got: " + customScopes + "; provider: " + scopesProvider + " (" + scopesProvider.getClass() + ")", null, scopesProvider.getClass());
      }
      allScopes.addAll(customScopes);
    }
    return new ArrayList<>(allScopes);
  }
}
