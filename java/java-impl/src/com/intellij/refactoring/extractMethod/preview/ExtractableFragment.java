// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
class ExtractableFragment {
  private final SmartPsiElementPointer<PsiElement> myStart;
  private final SmartPsiElementPointer<PsiElement> myEnd;

  public ExtractableFragment(@NotNull PsiElement start, @NotNull PsiElement end) {
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(start.getProject());
    myStart = smartPointerManager.createSmartPsiElementPointer(start);
    myEnd = start != end ? smartPointerManager.createSmartPsiElementPointer(end) : myStart;
  }

  public ExtractableFragment(@NotNull PsiElement[] elements) {
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

  @Nullable
  public Navigatable getNavigatable() {
    PsiElement start = myStart != null ? myStart.getElement() : null;
    return start instanceof Navigatable ? (Navigatable)start : null;
  }

  @Nullable
  public TextRange getTextRange() {
    if (myStart == null || myEnd == null) {
      return null;
    }
    PsiElement start = myStart.getElement();
    if (myStart == myEnd) {
      return start != null ? start.getTextRange() : null;
    }
    PsiElement end = myEnd.getElement();
    if (start == null || end == null) {
      return null;
    }
    return new TextRange(start.getTextRange().getStartOffset(), end.getTextRange().getEndOffset());
  }

  @Nullable
  public static TextRange getTextRange(@NotNull PsiElement[] elements) {
    if (elements.length == 0) {
      return null;
    }
    if (elements.length == 1) {
      return elements[0].getTextRange();
    }
    return new TextRange(elements[0].getTextRange().getStartOffset(), elements[elements.length - 1].getTextRange().getEndOffset());
  }
}
