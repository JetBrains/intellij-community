// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures;

import com.intellij.psi.PsiElement;

public class PsiSubstitutionFactory {

  public static Runnable createAddAfter(PsiElement anchor, PsiElement element){
    return new AddSubstitution(Place.After, anchor, element);
  }

  public static Runnable createAddBefore(PsiElement anchor, PsiElement element){
    return new AddSubstitution(Place.Before, anchor, element);
  }

  public static Runnable createReplace(PsiElement source, PsiElement target){
    return new ReplaceSubstitution(source, target);
  }

  private static class AddSubstitution implements Runnable {
    public final Place place;
    public final PsiElement anchor;
    public final PsiElement element;

    private AddSubstitution(Place place, PsiElement anchor, PsiElement element) {
      this.place = place;
      this.anchor = anchor;
      this.element = element;
    }

    @Override
    public void run() {
      switch (place) {
        case Before:
          anchor.getParent().addBefore(element, anchor);
          break;
        case After:
          anchor.getParent().addAfter(element, anchor);
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  private static class ReplaceSubstitution implements Runnable {
    public final PsiElement source;
    public final PsiElement target;

    private ReplaceSubstitution(PsiElement source, PsiElement target) {
      this.source = source;
      this.target = target;
    }

    @Override
    public void run() {
      source.replace(target);
    }
  }

  private enum Place {
    Before, After
  }
}