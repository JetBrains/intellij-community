// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import com.intellij.model.Symbol;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface and register it as "com.intellij.symbolPresentation" extension
 * to customize the appearance of symbols.
 *
 * @see PresentableSymbol
 */
public interface SymbolPresentationProvider {

  @Nullable SymbolPresentation getPresentation(@NotNull Symbol symbol);
}
