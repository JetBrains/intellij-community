// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@FunctionalInterface
public interface CustomScopesProvider {
  ProjectExtensionPointName<CustomScopesProvider> CUSTOM_SCOPES_PROVIDER = new ProjectExtensionPointName<>("com.intellij.customScopesProvider");

  @NotNull List<NamedScope> getCustomScopes();

  default @NotNull List<NamedScope> getFilteredScopes() {
    return ContainerUtil.filter(getCustomScopes(), scope -> {
      for (CustomScopesFilter filter : CustomScopesFilter.EP_NAME.getIterable()) {
        if (filter.excludeScope(scope)) {
          return false;
        }
      }
      return true;
    });
  }
}