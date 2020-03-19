// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import com.intellij.model.Symbol;
import com.intellij.navigation.TargetPopupPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this interface in the {@link Symbol} to customize its appearance.
 *
 * @see SymbolPresentationProvider
 */
public interface PresentableSymbol extends Symbol {

  @NotNull SymbolPresentation getSymbolPresentation();

  /**
   * Implement this method to customize appearance of the symbol in the popup,
   * which is shown when there are several symbols to choose from.
   * <p>
   * Be default the popup presentation is derived from the {@link #getSymbolPresentation()}
   */
  default @Nullable TargetPopupPresentation getPopupPresentation() {
    return null;
  }
}
