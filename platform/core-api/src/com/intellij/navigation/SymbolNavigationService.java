// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.model.Symbol;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * This is an entry point to obtain {@link NavigationTarget}s of a {@link Symbol}.
 * <p/>
 * Implement {@link NavigatableSymbol} in the {@link Symbol}
 * or implement a {@link SymbolNavigationProvider} extension
 * to customize navigation targets of the {@link Symbol}.
 */
@ApiStatus.Experimental
public interface SymbolNavigationService {

  @NotNull
  static SymbolNavigationService getInstance() {
    return ServiceManager.getService(SymbolNavigationService.class);
  }

  @NotNull
  Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project, @NotNull Symbol symbol);
}
