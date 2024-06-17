// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.model.Pointer;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiElementRef<T extends PsiElement> implements Pointer<T> {
  private volatile PsiRefColleague<T> myColleague;

  PsiElementRef(PsiRefColleague<T> colleague) {
    myColleague = colleague;
  }

  public boolean isImaginary() {
    return getPsiElement() == null;
  }

  public @Nullable T getPsiElement() {
    return myColleague.getPsiElement();
  }

  public @NotNull T ensurePsiElementExists() {
    final PsiRefColleague.Real<T> realColleague = myColleague.makeReal();
    myColleague = realColleague;
    return realColleague.getPsiElement();
  }

  public @NotNull PsiElement getRoot() {
    return myColleague.getRoot();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PsiElementRef && myColleague.equals(((PsiElementRef<?>) o).myColleague);
  }

  @Override
  public int hashCode() {
    return myColleague.hashCode();
  }

  public boolean isValid() {
    return myColleague.isValid();
  }

  @Override
  public @Nullable T dereference() {
    return getPsiElement();
  }

  public static <T extends PsiElement> PsiElementRef<T> real(@NotNull T element) {
    return new PsiElementRef<>(new PsiRefColleague.Real<>(element));
  }

  public static <Child extends PsiElement, Parent extends PsiElement> PsiElementRef<Child> imaginary(final PsiElementRef<? extends Parent> parent, final PsiRefElementCreator<? super Parent, ? extends Child> creator) {
    return new PsiElementRef<>(new PsiRefColleague.Imaginary<>(parent, creator));
  }

  public PsiManager getPsiManager() {
    return myColleague.getRoot().getManager();
  }

  private interface PsiRefColleague<T extends PsiElement> {

    boolean isValid();

    @Nullable
    T getPsiElement();

    @NotNull
    Real<T> makeReal();

    @NotNull
    PsiElement getRoot();

    class Real<T extends PsiElement> implements PsiRefColleague<T> {
      private final T myElement;

      public Real(@NotNull T element) {
        PsiUtilCore.ensureValid(element);
        myElement = element;
      }

      @Override
      public @NotNull T getPsiElement() {
        return myElement;
      }

      @Override
      public boolean isValid() {
        return myElement.isValid();
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Real<?> real = (Real<?>)o;

        if (!myElement.equals(real.myElement)) return false;

        return true;
      }

      @Override
      public int hashCode() {
        return myElement.hashCode();
      }

      @Override
      public @NotNull Real<T> makeReal() {
        return this;
      }

      @Override
      public @NotNull PsiElement getRoot() {
        return myElement;
      }
    }

    class Imaginary<Child extends PsiElement, Parent extends PsiElement> implements PsiRefColleague<Child> {
      private final PsiElementRef<? extends Parent> myParent;
      private final PsiRefElementCreator<? super Parent, ? extends Child> myCreator;

      public Imaginary(PsiElementRef<? extends Parent> parent, PsiRefElementCreator<? super Parent, ? extends Child> creator) {
        myParent = parent;
        myCreator = creator;
      }

      @Override
      public boolean isValid() {
        return myParent.isValid();
      }

      @Override
      public Child getPsiElement() {
        return null;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Imaginary<?, ?> imaginary = (Imaginary<?, ?>)o;

        if (!myCreator.equals(imaginary.myCreator)) return false;
        if (!myParent.equals(imaginary.myParent)) return false;

        return true;
      }

      @Override
      public int hashCode() {
        int result = myParent.hashCode();
        result = 31 * result + myCreator.hashCode();
        return result;
      }

      @Override
      public @NotNull Real<Child> makeReal() {
        return new Real<>(myCreator.createChild(myParent.ensurePsiElementExists()));
      }

      @Override
      public @NotNull PsiElement getRoot() {
        return myParent.getRoot();
      }
    }
  }
}
