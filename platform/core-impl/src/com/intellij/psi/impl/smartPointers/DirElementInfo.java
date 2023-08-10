// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DirElementInfo extends SmartPointerElementInfo {
  private final @NotNull VirtualFile myVirtualFile;
  private final @NotNull Project myProject;


  DirElementInfo(@NotNull PsiDirectory directory) {
    myProject = directory.getProject();
    myVirtualFile = directory.getVirtualFile();
  }

  @Override
  PsiElement restoreElement(@NotNull SmartPointerManagerImpl manager) {
    return SelfElementInfo.restoreDirectoryFromVirtual(myVirtualFile, myProject);
  }

  @Override
  PsiFile restoreFile(@NotNull SmartPointerManagerImpl manager) {
    return null;
  }

  @Override
  int elementHashCode() {
    return myVirtualFile.hashCode();
  }

  @Override
  boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other,
                                   @NotNull SmartPointerManagerImpl manager) {
    return other instanceof DirElementInfo && Comparing.equal(myVirtualFile, ((DirElementInfo)other).myVirtualFile);
  }

  @NotNull
  @Override
  VirtualFile getVirtualFile() {
    return myVirtualFile;
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
  public String toString() {
    return "dir{" + myVirtualFile + "}";
  }
}
