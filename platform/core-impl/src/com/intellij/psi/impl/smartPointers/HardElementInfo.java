// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

/**
 * Holds a direct (hard) reference to a {@link PsiElement}. No range tracking or survival across
 * reparse — used for non-physical PSI that cannot be restored by position (e.g., light elements).
 */
class HardElementInfo extends SmartPointerElementInfo {
  private final @NotNull PsiElement myElement;

  HardElementInfo(@NotNull PsiElement element) {
    myElement = element;
  }

  @Override
  PsiElement restoreElement(@NotNull SmartPointerManagerEx manager) {
    return myElement;
  }

  @Override
  PsiFile restoreFile(@NotNull SmartPointerManagerEx manager) {
    return myElement.isValid() ? myElement.getContainingFile() : null;
  }

  @Override
  int elementHashCode() {
    return myElement.hashCode();
  }

  @Override
  boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other, @NotNull SmartPointerManagerEx manager) {
    return other instanceof HardElementInfo && myElement.equals(((HardElementInfo)other).myElement);
  }

  @Override
  VirtualFile getVirtualFile() {
    return PsiUtilCore.getVirtualFile(myElement);
  }

  @Override
  Segment getRange(@NotNull SmartPointerManagerEx manager) {
    return myElement.getTextRange();
  }

  @Override
  Segment getPsiRange(@NotNull SmartPointerManagerEx manager) {
    return getRange(manager);
  }

  @Override
  public String toString() {
    return "hard{" + myElement + " of " + myElement.getClass() + "}";
  }
}
