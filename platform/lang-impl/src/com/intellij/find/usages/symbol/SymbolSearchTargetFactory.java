// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.symbol;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.model.Symbol;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.annotations.ApiStatus.OverrideOnly;

/**
 * To provide a {@link SearchTarget} by a Symbol, either:
 * <ul>
 * <li>implement {@link SymbolSearchTargetFactory} and register as {@code com.intellij.lang.symbolSearchTarget} extension</li>
 * <li>implement {@link SearchableSymbol} in a Symbol to provide search target for the symbol</li>
 * <li>implement {@link SearchTarget} in a Symbol</li>
 * </ul>
 * Several symbols might have {@link SearchTarget#equals equal} targets,
 * in this case any target will be chosen and used instead of showing the disambiguation popup.
 */
@OverrideOnly
public interface SymbolSearchTargetFactory<T extends Symbol> {

  @Nullable
  SearchTarget createTarget(@NotNull Project project, @NotNull T symbol);
}
