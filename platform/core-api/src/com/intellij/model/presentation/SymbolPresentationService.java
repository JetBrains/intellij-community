// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import com.intellij.model.Symbol;
import com.intellij.navigation.TargetPopupPresentation;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

/**
 * This is an entry point to obtain presentation of a {@link Symbol}.
 * <p/>
 * Implement {@link PresentableSymbol} in the {@link Symbol}
 * or implement a {@link SymbolPresentationProvider} extension
 * to customize appearance of the {@link Symbol}.
 */
public interface SymbolPresentationService {

  static @NotNull SymbolPresentationService getInstance() {
    return ServiceManager.getService(SymbolPresentationService.class);
  }

  @NotNull SymbolPresentation getSymbolPresentation(@NotNull Symbol symbol);

  @NotNull TargetPopupPresentation getPopupPresentation(@NotNull Symbol symbol);
}
