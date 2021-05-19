// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Experimental
public interface SymbolTypeProvider {

  @ApiStatus.Internal
  ExtensionPointName<SymbolTypeProvider> EP_NAME = ExtensionPointName.create("com.intellij.lang.symbolTypeProvider");

  /**
   * Entry point for obtaining type symbols from a symbol declaration,
   * e.g., a symbol corresponding to local variable type when the caret is within declaration of such variable.
   * <p/>
   * Default implementation delegates to {@link #getSymbolTypes(Symbol)}.
   * If {@code declaration} is not needed, then {@link #getSymbolTypes(Symbol)} may be implemented instead.
   */
  default @NotNull List<? extends @NotNull Symbol> getSymbolTypes(@NotNull PsiSymbolDeclaration declaration) {
    return getSymbolTypes(declaration.getSymbol());
  }

  /**
   * Entry point for obtaining type symbols from a symbol reference,
   * e.g., a symbol corresponding to local variable type when the caret is within a reference on a variable..
   * <p/>
   * Default implementation delegates to {@link #getSymbolTypes(Symbol)}.
   * If {@code reference} is not needed, then {@link #getSymbolTypes(Symbol)} may be implemented instead.
   */
  default @NotNull List<? extends @NotNull Symbol> getSymbolTypes(@NotNull PsiSymbolReference reference) {
    List<Symbol> list = new SmartList<>();
    for (Symbol it : reference.resolveReference()) {
      list.addAll(getSymbolTypes(it));
    }
    return list;
  }

  /**
   * This method is not called by the platform.
   * This method exists to simplify implementation of this interface.
   * This method is default to allow implementation
   * of {@link #getSymbolTypes(PsiSymbolDeclaration)} and/or {@link #getSymbolTypes(PsiSymbolReference)}
   * without having to implement this method.
   */
  @OverrideOnly
  default @NotNull List<? extends @NotNull Symbol> getSymbolTypes(@SuppressWarnings("unused") @NotNull Symbol symbol) {
    throw new AbstractMethodError();
  }
}
