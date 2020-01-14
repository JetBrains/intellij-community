// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.structures;

import com.intellij.psi.PsiElement;

public class PsiSubstitutionFactory {

  public static PsiSubstitution createAddAfter(PsiElement anchor, PsiElement element){
    return new AddSubstitution(Place.After, anchor, element);
  }

  public static PsiSubstitution createAddBefore(PsiElement anchor, PsiElement element){
    return new AddSubstitution(Place.Before, anchor, element);
  }

  public static PsiSubstitution createReplace(PsiElement source, PsiElement target){
    return new ReplaceSubstitution(source, target);
  }

  private static class AddSubstitution implements PsiSubstitution {
    public final Place place;
    public final PsiElement anchor;
    public final PsiElement element;

    private AddSubstitution(Place place, PsiElement anchor, PsiElement element) {
      this.place = place;
      this.anchor = anchor;
      this.element = element;
    }

    @Override
    public Runnable getAction() {
      switch (place) {
        case Before:
          return () -> anchor.getParent().addBefore(element, anchor);
        case After:
          return () -> anchor.getParent().addAfter(element, anchor);
        default:
          throw new IllegalStateException();
      }
    }
  }

  private static class ReplaceSubstitution implements PsiSubstitution {
    public final PsiElement source;
    public final PsiElement target;

    private ReplaceSubstitution(PsiElement source, PsiElement target) {
      this.source = source;
      this.target = target;
    }

    @Override
    public Runnable getAction() {
      return () -> source.replace(target);
    }
  }

  private enum Place {
    Before, After
  }
}