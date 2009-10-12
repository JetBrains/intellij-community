/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public final class PsiRef<T extends PsiElement> {
  private volatile PsiRefColleague<T> myColleague;

  public PsiRef(PsiRefColleague<T> colleague) {
    myColleague = colleague;
  }

  public final boolean isImaginary() {
    return getPsiElement() == null;
  }

  @Nullable
  public final T getPsiElement() {
    return myColleague.getPsiElement();
  }

  @NotNull
  public final T ensurePsiElementExists() {
    final PsiRefColleague.Real<T> realColleague = myColleague.makeReal();
    myColleague = realColleague;
    return realColleague.getPsiElement();
  }

  @NotNull
  public final PsiElement getRoot() {
    return myColleague.getRoot();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PsiRef && myColleague.equals(((PsiRef) o).myColleague);
  }

  @Override
  public int hashCode() {
    return myColleague.hashCode();
  }

  public final boolean isValid() {
    return myColleague.isValid();
  }

  public static <T extends PsiElement> PsiRef<T> real(final T element) {
    return new PsiRef<T>(new PsiRefColleague.Real<T>(element));
  }

  public static <Child extends PsiElement, Parent extends PsiElement> PsiRef<Child> imaginary(final PsiRef<? extends Parent> parent, final PsiRefElementCreator<Parent, Child> creator) {
    return new PsiRef<Child>(new PsiRefColleague.Imaginary<Child, Parent>(parent, creator));
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

      public Real(T element) {
        myElement = element;
      }

      @NotNull
        public T getPsiElement() {
        return myElement;
      }

      public boolean isValid() {
        return myElement.isValid();
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Real real = (Real)o;

        if (!myElement.equals(real.myElement)) return false;

        return true;
      }

      @Override
      public int hashCode() {
        return myElement.hashCode();
      }

      @NotNull
      public Real<T> makeReal() {
        return this;
      }

      @NotNull
      public PsiElement getRoot() {
        return myElement;
      }
    }

    class Imaginary<Child extends PsiElement, Parent extends PsiElement> implements PsiRefColleague<Child> {
      private final PsiRef<? extends Parent> myParent;
      private final PsiRefElementCreator<Parent, Child> myCreator;

      public Imaginary(PsiRef<? extends Parent> parent, PsiRefElementCreator<Parent, Child> creator) {
        myParent = parent;
        myCreator = creator;
      }

      public boolean isValid() {
        return myParent.isValid();
      }

      public Child getPsiElement() {
        return null;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Imaginary imaginary = (Imaginary)o;

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

      @NotNull
      public Real<Child> makeReal() {
        return new Real<Child>(myCreator.createChild(myParent.ensurePsiElementExists()));
      }

      @NotNull
      public PsiElement getRoot() {
        return myParent.getRoot();
      }
    }


  }

}
