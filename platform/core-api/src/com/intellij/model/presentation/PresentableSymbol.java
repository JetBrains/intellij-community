// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.presentation;

import com.intellij.model.Symbol;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface in the {@link Symbol} to customize its appearance.
 *
 * @deprecated This is a wrong concept for general API: Symbols can't have presentation.
 * The platform does not use this interface anymore, so, if needed, use own analog of this interface in your subsystem/plugin.
 */
@ScheduledForRemoval
@Deprecated
public interface PresentableSymbol extends Symbol {

  @NotNull SymbolPresentation getSymbolPresentation();
}
