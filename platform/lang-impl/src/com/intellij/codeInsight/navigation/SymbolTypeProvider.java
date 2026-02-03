// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Experimental
public interface SymbolTypeProvider {

  @ApiStatus.Internal
  ExtensionPointName<SymbolTypeProvider> EP_NAME = ExtensionPointName.create("com.intellij.lang.symbolTypeProvider");

  /**
   * Entry point for obtaining type symbols from a symbol,
   * e.g., a symbol corresponding to local variable type
   * when the caret is within a declaration of such variable,
   * or within a reference to this variable.
   */
  @NotNull List<? extends @NotNull Symbol> getSymbolTypes(@NotNull Project project, @NotNull Symbol symbol);
}
