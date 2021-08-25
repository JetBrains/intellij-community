// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.SearchScopeProvidingRunProfile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ExecutionSearchScopes {
  public static @NotNull GlobalSearchScope executionScope(@NotNull Project project, @Nullable RunProfile runProfile) {
    if (runProfile instanceof SearchScopeProvidingRunProfile) {
      GlobalSearchScope scope = ((SearchScopeProvidingRunProfile)runProfile).getSearchScope();
      if (scope != null) return scope;
    }
    return GlobalSearchScope.allScope(project);
  }

  public static @Nullable GlobalSearchScope executionScope(@NotNull Collection<? extends Module> modules) {
    if (modules.isEmpty()) {
      return null;
    }
    return GlobalSearchScope.union(ContainerUtil.map2List(modules, module -> {
      return GlobalSearchScope.moduleRuntimeScope(module, true);
    }));
  }
}
