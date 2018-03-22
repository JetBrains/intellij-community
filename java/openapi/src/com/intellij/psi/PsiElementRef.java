// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.jvm.JvmAnnotationTreeElement;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public final class PsiElementRef<T extends JvmAnnotationTreeElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiElementRef");
  private volatile PsiRefColleague<T> myColleague;

  public PsiElementRef(PsiRefColleague<T> colleague) {
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
    throw new UnsupportedOperationException("Not implemented");
    //final PsiRefColleague.Real<T> realColleague = myColleague.makeReal();
    //myColleague = realColleague;
    //return realColleague.getPsiElement();
  }

  @NotNull
  public final JvmAnnotationTreeElement getRoot() {
    return myColleague.getRoot();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PsiElementRef && myColleague.equals(((PsiElementRef) o).myColleague);
  }

  @Override
  public int hashCode() {
    return myColleague.hashCode();
  }

  public final boolean isValid() {
    return myColleague.isValid();
  }

  public static <T extends JvmAnnotationTreeElement> PsiElementRef<T> real(@NotNull final T element) {
    return new PsiElementRef<>(new PsiRefColleague.Real<>(element));
  }

  public static <Child extends JvmAnnotationTreeElement, Parent extends JvmAnnotationTreeElement> PsiElementRef<Child> imaginary(final PsiElementRef<? extends Parent> parent) {
    return new PsiElementRef<>(new PsiRefColleague.Imaginary<>(parent));
  }

  public PsiManager getPsiManager() {
    JvmAnnotationTreeElement root = myColleague.getRoot();
    return JvmPsiConversionHelper.getInstance(JvmPsiConversionHelper.getProject(root)).convertJvmTreeElement(root).getManager();
  }

  private interface PsiRefColleague<T extends JvmAnnotationTreeElement> {

    boolean isValid();

    @Nullable
    T getPsiElement();

    //@NotNull
    //Real<T> makeReal();

    @NotNull
    JvmAnnotationTreeElement getRoot();

    class Real<T extends JvmAnnotationTreeElement> implements PsiRefColleague<T> {
      private final T myElement;

      public Real(@NotNull T element) {
        //PsiUtilCore.ensureValid(element);
        myElement = element;
      }

      @Override
      @NotNull
      public T getPsiElement() {
        return myElement;
      }

      @Override
      public boolean isValid() {
        return true; // TODO: implement
        //return myElement.isValid();
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

      //@Override
      //@NotNull
      //public Real<T> makeReal() {
      //  return this;
      //}

      @Override
      @NotNull
      public JvmAnnotationTreeElement getRoot() {
        return myElement;
      }
    }

    class Imaginary<Child extends JvmAnnotationTreeElement, Parent extends JvmAnnotationTreeElement> implements PsiRefColleague<Child> {
      private final PsiElementRef<? extends Parent> myParent;
      //private final PsiRefElementCreator<Parent, Child> myCreator;

      public Imaginary(PsiElementRef<? extends Parent> parent) {
        myParent = parent;
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

        Imaginary imaginary = (Imaginary)o;

        //if (!myCreator.equals(imaginary.myCreator)) return false;
        if (!myParent.equals(imaginary.myParent)) return false;

        return true;
      }

      @Override
      public int hashCode() {
        int result = myParent.hashCode();

        return result;
      }

      //@Override
      //@NotNull
      //public Real<Child> makeReal() {
      //  return new Real<>(myCreator.createChild(myParent.ensurePsiElementExists()));
      //}

      @Override
      @NotNull
      public JvmAnnotationTreeElement getRoot() {
        return myParent.getRoot();
      }
    }


  }

}
