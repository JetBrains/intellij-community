// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.analysis;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.ProjectProductionScope;
import com.intellij.psi.search.scope.TestsScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class PackagesScopesProvider extends CustomScopesProviderEx {
  private final List<NamedScope> myScopes;

  public static PackagesScopesProvider getInstance(Project project) {
    return Extensions.findExtension(CUSTOM_SCOPES_PROVIDER, project, PackagesScopesProvider.class);
  }

  public PackagesScopesProvider() {
    myScopes = Arrays.asList(ProjectProductionScope.INSTANCE, TestsScope.INSTANCE);
  }

  @Override
  @NotNull
  public List<NamedScope> getCustomScopes() {
    return myScopes;
  }

  public NamedScope getProjectProductionScope() {
    return ProjectProductionScope.INSTANCE;
  }
}