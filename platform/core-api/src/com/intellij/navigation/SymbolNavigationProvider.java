// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.model.Symbol;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.navigation.NavigationTarget;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Implement this interface and register it as "com.intellij.symbolNavigation" extension
 * to customize the navigation targets of symbols.
 *
 * @see NavigatableSymbol
 */
@Experimental
@OverrideOnly
public interface SymbolNavigationProvider {

  @NotNull
  @Unmodifiable
  Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project, @NotNull Symbol symbol);
}
