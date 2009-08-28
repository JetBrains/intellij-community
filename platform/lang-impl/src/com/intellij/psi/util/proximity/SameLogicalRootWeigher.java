/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util.proximity;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.ProximityLocation;
import com.intellij.util.LogicalRoot;
import com.intellij.util.LogicalRootsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class SameLogicalRootWeigher extends ProximityWeigher {

  public Comparable weigh(@NotNull final PsiElement element, final ProximityLocation location) {
    final LogicalRoot elementRoot = findLogicalRoot(element);
    final LogicalRoot contextRoot = findLogicalRoot(location.getPosition());
    return elementRoot != null && contextRoot != null && elementRoot.equals(contextRoot);
  }

  @Nullable
  private static LogicalRoot findLogicalRoot(PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return null;

    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return null;

    return LogicalRootsManager.getLogicalRootsManager(element.getProject()).findLogicalRoot(file);
  }
}