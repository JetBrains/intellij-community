// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class WrappedElementAnchor extends PsiAnchor {
  private final SmartPointerAnchorProvider myAnchorProvider;
  private final PsiAnchor myBaseAnchor;

  public WrappedElementAnchor(@NotNull SmartPointerAnchorProvider provider, @NotNull PsiAnchor anchor) {
    myAnchorProvider = provider;
    myBaseAnchor = anchor;
  }

  @Nullable
  @Override
  public PsiElement retrieve() {
    PsiElement baseElement = myBaseAnchor.retrieve();
    return baseElement == null ? null : myAnchorProvider.restoreElement(baseElement);
  }

  @Override
  public PsiFile getFile() {
    PsiElement element = retrieve();
    return element == null ? null : element.getContainingFile();
  }

  @Override
  public int getStartOffset() {
    PsiElement element = retrieve();
    return element == null || element.getTextRange() == null ? -1 : element.getTextRange().getStartOffset();
  }

  @Override
  public int getEndOffset() {
    PsiElement element = retrieve();
    return element == null || element.getTextRange() == null ? -1 : element.getTextRange().getEndOffset();
  }

  @Override
  public String toString() {
    return "WrappedElementAnchor(" + myBaseAnchor + "; provider=" + myAnchorProvider + ")";
  }
}
