// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;


public final class NavigationItemFileStatus {
  private NavigationItemFileStatus() {
  }

  public static FileStatus get(NavigationItem item) {
    if (item instanceof PsiElement) {
      return getPsiElementFileStatus((PsiElement)item);
    }
    if (item instanceof AbstractTreeNode) {
      return ((AbstractTreeNode<?>) item).getFileStatus();
    }
    if (item instanceof PsiElementNavigationItem) {
      PsiElement target = ((PsiElementNavigationItem) item).getTargetElement();
      if (target != null) return getPsiElementFileStatus(target);
    }
    return FileStatus.NOT_CHANGED;
  }

  private static FileStatus getPsiElementFileStatus(@NotNull PsiElement psiElement) {
    if (!psiElement.isPhysical()) return FileStatus.NOT_CHANGED;
    PsiFile contFile = psiElement.getContainingFile();
    if (contFile == null) return FileStatus.NOT_CHANGED;
    VirtualFile vFile = contFile.getVirtualFile();
    return vFile == null ? FileStatus.NOT_CHANGED : FileStatusManager.getInstance(psiElement.getProject()).getStatus(vFile);
  }
}
