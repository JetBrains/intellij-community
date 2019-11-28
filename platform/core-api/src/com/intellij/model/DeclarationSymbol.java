// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;

/**
 * Convenience interface, representing a symbol which is also a declaration of itself:
 * <pre>
 * SymbolDeclaration                d
 *                                  ↕
 * Symbol                           s
 * </pre>
 */
public interface DeclarationSymbol extends Symbol, SymbolDeclaration {

  @NotNull
  @Override
  Pointer<? extends DeclarationSymbol> createPointer();

  @NotNull
  @Override
  default Symbol getSymbol() {
    return this;
  }
}
