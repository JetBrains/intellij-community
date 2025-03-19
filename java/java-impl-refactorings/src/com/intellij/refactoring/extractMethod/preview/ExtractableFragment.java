// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ExtractableFragment {
  private final SmartPsiElementPointer<PsiElement> myStart;
  private final SmartPsiElementPointer<PsiElement> myEnd;

  ExtractableFragment(@NotNull PsiElement start, @NotNull PsiElement end) {
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(start.getProject());
    myStart = smartPointerManager.createSmartPsiElementPointer(start);
    myEnd = start != end ? smartPointerManager.createSmartPsiElementPointer(end) : myStart;
  }

  ExtractableFragment(PsiElement @NotNull [] elements) {
    if (elements.length == 0) {
      myStart = null;
      myEnd = null;
      return;
    }
    PsiElement start = elements[0];
    PsiElement end = elements[elements.length - 1];
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(start.getProject());
    myStart = smartPointerManager.createSmartPsiElementPointer(start);
    myEnd = start != end ? smartPointerManager.createSmartPsiElementPointer(end) : myStart;
  }

  public @Nullable Navigatable getNavigatable() {
    PsiElement start = myStart != null ? myStart.getElement() : null;
    return start instanceof Navigatable ? (Navigatable)start : null;
  }

  public @Nullable ElementsRange getElementsRange() {
    if (myStart == null || myEnd == null) {
      return null;
    }
    PsiElement start = myStart.getElement();
    if (myStart == myEnd) {
      return start != null ? new ElementsRange(start, start) : null;
    }
    PsiElement end = myEnd.getElement();
    if (start == null || end == null) {
      return null;
    }
    return new ElementsRange(start, end);
  }

  public @Nullable TextRange getTextRange() {
    ElementsRange elementsRange = getElementsRange();
    return elementsRange != null ? elementsRange.getTextRange() : null;
  }
}
