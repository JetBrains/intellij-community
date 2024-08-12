// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi.impl;

import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This symbol intentionally does not have {@link #equals} or {@link #hashCode}
 * implementation, since nobody is supposed to use its equivalence in any meaningful way.
 * <p>
 * See {@link com.intellij.model.psi.PsiSymbolService#extractElementFromSymbol}
 * if you want to check if two such symbols point to the same PSI.
 *
 * @see com.intellij.model.psi.PsiSymbolService
 */
final class Psi2Symbol implements Symbol {

  private final @NotNull PsiElement myElement;
  private final @NotNull Pointer<Psi2Symbol> myPointer;

  Psi2Symbol(@NotNull PsiElement element) {
    this(element, new MyPointer(element));
  }

  Psi2Symbol(@NotNull PsiElement element, @NotNull Pointer<Psi2Symbol> pointer) {
    myElement = element;
    myPointer = pointer;
  }

  @NotNull
  PsiElement getElement() {
    return myElement;
  }

  @Override
  public @NotNull Pointer<Psi2Symbol> createPointer() {
    return myPointer;
  }

  private static final class MyPointer implements Pointer<Psi2Symbol> {

    private final @NotNull Pointer<? extends PsiElement> myPointer;

    private MyPointer(@NotNull PsiElement element) {
      myPointer = SmartPointerManager.createPointer(element);
    }

    @Override
    public @Nullable Psi2Symbol dereference() {
      PsiElement element = myPointer.dereference();
      if (element == null) {
        return null;
      }
      else {
        return new Psi2Symbol(element, this);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyPointer pointer = (MyPointer)o;

      if (!myPointer.equals(pointer.myPointer)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myPointer.hashCode();
    }
  }
}
