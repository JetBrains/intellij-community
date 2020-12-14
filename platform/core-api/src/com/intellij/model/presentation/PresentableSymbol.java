// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import com.intellij.model.Symbol;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface in the {@link Symbol} to customize its appearance.
 *
 * @see SymbolPresentationProvider
 */
public interface PresentableSymbol extends Symbol {

  @NotNull SymbolPresentation getSymbolPresentation();
}
