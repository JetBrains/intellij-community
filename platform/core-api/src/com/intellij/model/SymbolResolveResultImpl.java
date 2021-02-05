// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

final class SymbolResolveResultImpl implements SymbolResolveResult {

  private final @NotNull Symbol mySymbol;

  SymbolResolveResultImpl(@NotNull Symbol symbol) {
    mySymbol = symbol;
  }

  @Override
  public @NotNull Symbol getTarget() {
    return mySymbol;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SymbolResolveResultImpl result = (SymbolResolveResultImpl)o;
    return mySymbol.equals(result.mySymbol);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mySymbol);
  }
}
