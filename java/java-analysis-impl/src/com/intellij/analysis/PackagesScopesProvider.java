// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.ProjectProductionScope;
import com.intellij.psi.search.scope.TestsScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public final class PackagesScopesProvider extends CustomScopesProviderEx {
  private final List<NamedScope> myScopes;

  public static PackagesScopesProvider getInstance(Project project) {
    return CUSTOM_SCOPES_PROVIDER.findExtension(PackagesScopesProvider.class, project);
  }

  public PackagesScopesProvider() {
    myScopes = Arrays.asList(ProjectProductionScope.INSTANCE, TestsScope.INSTANCE);
  }

  @Override
  public @NotNull List<NamedScope> getCustomScopes() {
    return myScopes;
  }

  public NamedScope getProjectProductionScope() {
    return ProjectProductionScope.INSTANCE;
  }
}