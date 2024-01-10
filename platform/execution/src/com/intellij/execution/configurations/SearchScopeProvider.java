// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.ExecutionSearchScopes;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Vojtech Krasa
 * @deprecated Use {@link GlobalSearchScopes}
 */
@Deprecated
public final class SearchScopeProvider {

  /** @deprecated Use {@link ExecutionSearchScopes#executionScope(Collection)}*/
  @Deprecated(forRemoval = true)
  @NotNull
  public static GlobalSearchScope createSearchScope(@NotNull Project project, @Nullable RunProfile runProfile) {
    return ExecutionSearchScopes.executionScope(project, runProfile);
  }
}
