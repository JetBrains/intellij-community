// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import com.intellij.model.Symbol;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

/**
 * This is an entry point to obtain presentation of a {@link Symbol}.
 * <p/>
 * Implement {@link PresentableSymbol} in the {@link Symbol}
 * or implement a {@link SymbolPresentationProvider} extension
 * to customize appearance of the {@link Symbol}.
 */
public interface SymbolPresentationService {

  static @NotNull SymbolPresentationService getInstance() {
    return ApplicationManager.getApplication().getService(SymbolPresentationService.class);
  }

  static @Nls(capitalization = Sentence) @NotNull String getLongDescription(@NotNull Symbol symbol) {
    return getInstance().getSymbolPresentation(symbol).getLongDescription();
  }

  @NotNull SymbolPresentation getSymbolPresentation(@NotNull Symbol symbol);
}
