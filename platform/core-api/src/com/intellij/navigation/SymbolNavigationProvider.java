// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.model.Symbol;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Implement this interface and register it as "com.intellij.symbolNavigation" extension
 * to customize the navigation targets of symbols.
 *
 * @see NavigatableSymbol
 */
@ApiStatus.Experimental
public interface SymbolNavigationProvider {

  @NotNull
  Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project, @NotNull Symbol symbol);
}
