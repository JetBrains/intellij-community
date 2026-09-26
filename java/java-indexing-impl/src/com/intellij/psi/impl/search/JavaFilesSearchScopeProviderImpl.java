// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of {@link JavaFilesSearchScopeProvider}.
 */
public final class JavaFilesSearchScopeProviderImpl implements JavaFilesSearchScopeProvider {
  private final @NotNull Project myProject;

  public JavaFilesSearchScopeProviderImpl(@NotNull Project project) {
    myProject = project;
  }
  
  @Override
  public @NotNull GlobalSearchScope getScope() {
    return new JavaFilesSearchScope(myProject);
  }
}
