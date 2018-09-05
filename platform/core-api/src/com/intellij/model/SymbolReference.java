// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.navigation.NavigationService;
import com.intellij.navigation.NavigationTarget;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface SymbolReference {

  @NotNull
  Iterable<? extends SymbolResolveResult> resolveReference();

  @NotNull
  default Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project) {
    return NavigationService.getInstance(project).getNavigationTargets(this);
  }

  default boolean references(@NotNull Symbol target) {
    return ContainerUtil.or(resolveReference(), it -> it.getTarget().equals(target));
  }
}
