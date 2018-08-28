// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface NavigationService {

  static NavigationService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, NavigationService.class);
  }

  @NotNull
  Collection<? extends NavigationTarget> getNavigationTargets(@NotNull SymbolReference reference);

  @NotNull
  Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Symbol symbol);
}
