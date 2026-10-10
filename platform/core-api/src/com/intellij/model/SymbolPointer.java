// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import com.intellij.model.psi.PsiSymbolService;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A pointer to a {@link Symbol}, also to the symbol of a PSI element. It outlives read actions and dialogs.
 * <p>
 * <b>Two instances are different objects even for one symbol:</b> {@link Pointer} defines no equality, so this class has none
 * either, and it is compared by identity. Code that collects pointers compares the symbols with {@link #isSameSymbol} when it adds
 * one, and keeps the instance that is already there; after that, sets and maps of pointers work by identity.
 */
@ApiStatus.Experimental
public final class SymbolPointer {
  private final @NotNull Pointer<? extends Symbol> myPointer;

  private SymbolPointer(@NotNull Pointer<? extends Symbol> pointer) {
    myPointer = pointer;
  }

  @RequiresReadLock
  public static @NotNull SymbolPointer of(@NotNull PsiElement element) {
    return new SymbolPointer(PsiSymbolService.getInstance().asSymbol(element).createPointer());
  }

  /** @return false if the symbol is gone */
  @RequiresReadLock
  public boolean isValid() {
    return myPointer.dereference() != null;
  }

  /** @return the PSI element of the symbol, for code that works on PSI; null if the symbol is gone or is not a PSI element */
  @RequiresReadLock
  public @Nullable PsiElement element() {
    Symbol symbol = myPointer.dereference();
    return symbol == null ? null : PsiSymbolService.getInstance().extractElementFromSymbol(symbol);
  }

  /**
   * @return true if both point at one symbol that is not gone. A symbol of a PSI element has no equality, so two of them are compared
   * by their elements, as {@link PsiSymbolService#extractElementFromSymbol} asks; other symbols are compared with {@code equals}
   */
  @RequiresReadLock
  public boolean isSameSymbol(@NotNull SymbolPointer other) {
    Symbol symbol = myPointer.dereference();
    Symbol otherSymbol = other.myPointer.dereference();
    if (symbol == null || otherSymbol == null) return false;
    PsiSymbolService symbolService = PsiSymbolService.getInstance();
    PsiElement element = symbolService.extractElementFromSymbol(symbol);
    return element != null ? element.equals(symbolService.extractElementFromSymbol(otherSymbol)) : symbol.equals(otherSymbol);
  }
}
