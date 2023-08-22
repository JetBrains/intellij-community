// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public final class ExternalModuleBuildGlobalSearchScope extends DelegatingGlobalSearchScope {
  @NotNull
  private final String externalModulePath;

  public ExternalModuleBuildGlobalSearchScope(@NotNull Project project,
                                              @NotNull GlobalSearchScope baseScope,
                                              @NotNull String externalModulePath) {
    super(new DelegatingGlobalSearchScope(project, baseScope));
    this.externalModulePath = externalModulePath;
  }

  @NotNull
  public String getExternalModulePath() {
    return externalModulePath;
  }
}
