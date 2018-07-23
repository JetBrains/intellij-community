// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SearchScopeOptimizer {

  ExtensionPointName<SearchScopeOptimizer> CODE_USE_SCOPE_EP = ExtensionPointName.create("com.intellij.search.usageScopeOptimizer");

  @Nullable("is null when given optimizer can't provide a scope to restrict")
  SearchScope getRestrictedUseScope(@NotNull Project project, @NotNull Symbol symbol);
}
