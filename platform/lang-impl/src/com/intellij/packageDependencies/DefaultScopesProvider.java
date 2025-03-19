// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies;

import com.intellij.ide.scratch.ScratchesNamedScope;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.ProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public final class DefaultScopesProvider extends CustomScopesProviderEx {
  @SuppressWarnings({"FieldCanBeLocal", "unused"})
  private final Project myProject;
  private final List<NamedScope> myScopes;

  public static DefaultScopesProvider getInstance(Project project) {
    return CUSTOM_SCOPES_PROVIDER.findExtension(DefaultScopesProvider.class, project);
  }

  public DefaultScopesProvider(@NotNull Project project) {
    myProject = project;
    myScopes = Arrays.asList(ProjectFilesScope.INSTANCE,
                             getAllScope(),
                             NonProjectFilesScope.INSTANCE,
                             new ScratchesNamedScope());
  }

  @Override
  public @NotNull List<NamedScope> getCustomScopes() {
    return myScopes;
  }
}
