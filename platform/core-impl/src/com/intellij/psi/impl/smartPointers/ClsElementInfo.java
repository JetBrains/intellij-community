// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ClsElementInfo extends SmartPointerElementInfo {
  private final @NotNull PsiAnchor.StubIndexReference myStubIndexReference;

  ClsElementInfo(@NotNull PsiAnchor.StubIndexReference stubReference) {
    myStubIndexReference = stubReference;
  }

  @Override
  PsiElement restoreElement(@NotNull SmartPointerManagerImpl manager) {
    return myStubIndexReference.retrieve();
  }

  @Override
  int elementHashCode() {
    return myStubIndexReference.hashCode();
  }

  @Override
  boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other, @NotNull SmartPointerManagerImpl manager) {
    return other instanceof ClsElementInfo && myStubIndexReference.equals(((ClsElementInfo)other).myStubIndexReference);
  }

  @Override
  @NotNull
  VirtualFile getVirtualFile() {
    return myStubIndexReference.getVirtualFile();
  }

  @Override
  Segment getRange(@NotNull SmartPointerManagerImpl manager) {
    return null;
  }

  @Nullable
  @Override
  Segment getPsiRange(@NotNull SmartPointerManagerImpl manager) {
    return null;
  }

  @Override
  PsiFile restoreFile(@NotNull SmartPointerManagerImpl manager) {
    return myStubIndexReference.getFile();
  }

  @Override
  public String toString() {
    return myStubIndexReference.toString();
  }
}
